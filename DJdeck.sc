/* 
TODO
- test
    - interface functionality
    - beat sync accuracy
- review sound quality
- why can't I beatjump when paused?
- keep syncing enabled when new track is loaded
- allow unquantized beetjumping by default, and enable quantization via general quantization support for incoming midi/hid
    - how to support different clocks? No need, we just put everything on the master clock, this is okay because the entire idea behind quantized/delayed beatjumpig, is to keep everything lined up with ease


NICE TO HAVE
- get rid of popping when beatjumping
- reduce loading time of songs, make a function to preview songs which uses VDiskIn, which requires less buffer to be loaded as compared to ReadBuf
- more elaborate tempo functionality, see my thought in the MidiHidSystem-Guide.schelp
- BufRd has only single precision, so after 6.3min it becomes messy; I do like listening to long tracks... -> solution can be to overwrite the first part of the buffer when we start approaching the 6.3 min

DONE
- use shift-sync to enable sync, and copy tempo from slave to master
- tracks should not loop
- build synthdef which has DJ filter, using deadduck VST
- finish midi mapping
     - midi feedback
- looping functionality
- needledropping functionality (scrolling through track)
- try to do all time sensitive shit with setSynchronous
- pitchbend functionality
    - note userInducedOffset;
    - write pitchBendFunction which can be mapped similar as tracktor 
        - perhaps the dualTarget can be used for this, instead of assigning to button on off, assign to encoder forward backward -> for buttons I support providing two targets
        - create encoder sensitivity and acceleration settings (which can also be used for pitchbending; idea allow for the functions to respond to an argument and instead of making this always one let this depend on sensitivity and acceleration) -> Should distinguish between button, continous, encoderButton, and enconderContiunous
        -> added a new mode to the MidiCC object which does what we want, icm with a continuous target (not tested)
    - test whether buffer playback is synced
- keeping track of current play position:
    - append the track with a third channel which contains a ramp (not that easy to accomplisch as buffers can't be merged)
    - similar as above but use a standard ramp buffer, and read it in parallel (analyze whether these buffers are always synced)
    - use sendID of VDiskIn
- load track in sync
- tempo/phase syncing (see TrackClock)
    - make clock.beats leading by means of dependency
- .loadDouble; 
    - not working yet: it works when loading it directly as first track; it does not work if we have first loaded another track, synth.isRunning remains false, also when sending synth.run(true); no isRunning is not the problem (this was due to me not having launched a NodeWatcher)
    - main issue was due to bufnum changing when pointing buffer to the buffer of the other deck
- make reference buffer loading more efficient
    - referenceBuffer for each deck
        - should not get copied during loadDouble
        - is loaded in .init
        - in elongated when necessary using .loadCollection;
    - should write the buffer back and test whether this goed correctly, the appending
    - maybe simpler to have one referenceBuffer and see whether we can read this (at multiple decks) while appending it (or make it sufficiently long right from the start, check what the longest track is in our collection (should not be longer than an hour, make a buffer of an hour))
    -> quite a big change made, I switched to using BufRd
- before resetiting the deck, should we pause the synth? as to make sure we get not buffer issues/warnings
- save userinduced gridoffset with the track
    -> I also made sure that this is Archived, and loaded back in when we load the library
- synth and synthDef integration into project files
    - I added the synthDef to the class, such that we keep all related stuff in one document

NOT NEEDED
- I need to make sure we take into account the latency induced by the EnconderKernel when beatjumping/syncing phase between the decks clock and playback
    - the difficulty arises because we want to schedule beat jumps based on beats, however this latency if beat independent. There is no way to schedule an event x seconds prior to beat y
    - so you then need to reschedule the event if the tempo changes; this is a feature supercollider does not have; we could perhaps make some proxy object which we mute, and schedule another instance at the beat we want
    - but maybe it is more need to have a jumpAtFrame variable in the synthdef, if the current position becomes bigger, we have a positive edge, this we use to reset the sweep; this jumpAtFrame wil be constantly recalculated, taking into account also the latency; which it does not need to take into account.. because the frame we want to jump at remains unchanged, we want to arrive earlier at this frame; which is not hard to achieve with this route; 
        - use InRange.kr(in: in, lo: 1.0, hi: 1000.0) to determine when to beat jump
        -a drawback is though that we lose our hardSync approach, currently we can have drift in between beatJumps; but a beatJump glues the trackPlayback and the tempoClock
            - we can use the existing jumpToPosition to sync it
    - OR!! we just accept that the track is always running late by a set amount with respect to the clock; and we match this latency with the server communication induced latency for patterns; because this latency will always accur hence we do need some way of taking it into account [this corresponds with the manner that LinkClock talkes about latency and patterns]
        - benefit of BufRd is more and easier control over playback position
        - drawback of using PlayBuf is that we need a playAlong buffer in order to keep track of position; but maybe we can read this buffer from disk instead of from collection
        - with playbuf it is harder to maintain always x samples ahead (to compensate from kernelEncoder)
*/

DJdeck : Object {
    var deckNr;
    var <bus, <clock, <buffer, <synth, <referenceBus, <track;
    var trackTempo = 1, <quePoint = 0, <schedJump = false, <loop = false, beatJumpBeats = 4;
    var <trackBufferReady = false;
    var testBus, testBuffer;
    var userInducedGridOffsetTotal = 0, <userInducedGridOffsetStatic = 0;
    var <positionSetBus, <playerSelectionBus, <pauseBus;
    var <playerSelected = false;
    var <endOfTrackEvent = true; // this makes sure that we prepare for the endOfTrack initially

    // basic
    *new { |bus, target, addAction = 'addToHead', deckNr|
        ^super.new.init(bus, target, addAction, deckNr);
    }

    *initClass {
        this.addSynthDef;
    } 

    init { |b, target, addAction, deckNr_|
        deckNr = deckNr_;
        bus = b;
        clock = TrackClock.new(125/60).pause;
        clock.addDependant(this);
        referenceBus = Bus.control(numChannels: 2);
        positionSetBus = Bus.control;
        playerSelectionBus = Bus.control; // initialized to 0, which corresponds with playerSelected = false
        pauseBus = Bus.control;
        buffer = Buffer.loadCollection(Server.default,[0, 0, 0, 0], 2, action: {this.spawnSynths(target, addAction)} ); // use .loadCollection instead of .alloc because it allows to supply an action function; after the buffer is setup, we create the synth
        // ideally I would free the buffer here, but then the buffer will be free before the synth is created
        track = TrackDescription.newDummy(125, 6); // placeholder
    }

    // frontend: tracks
    loadTrack { |newTrack, action|
        if(clock.paused){ // we only load a track if no track is allready playing
            if(track.isNil.not){ this.reset }; // reset the deck if not done so yet
            track = newTrack;
            trackTempo = track.bpm/60;
            if(clock.sync.not){ clock.tempoInterface_(trackTempo) }; // play track at normal rate if not synced
            userInducedGridOffsetStatic = track.userInducedGridOffset; // get the tracks stored userInducedGridOffset
            clock.beats = this.position2beatAdjusted(0);
            // if we have not loaded a track, the synth is paused, so we need to activate the synth after loading
            buffer = track.loadBuffer(action: { trackBufferReady = true; this.reactivateSynth; action.value });
            synth.set(\trackTempo, trackTempo, \gain, track.preceivedDb.neg.dbamp);  // we set the tempo, and the gain, where gain is chosen such that the track ends up at 0dB again
            (deckNr.asString++", loadTrack: \t"++track.artist++", "++track.title).log(this);
        }{
            "track is still playing".postln;
        }
    }

    loadAndPlayTrack { |newTrack|
        this.loadTrack(newTrack, { this.play } );
    }

    loadDouble { |djDeck| //not working yet
        // should double the song allready playing on the deck, this is also the way we load songs: we copy them from the prelistenDeck
        if(clock.paused){ // we only load a track if no track is allready playing
            if(track.isNil.not){ this.reset }; // reset the deck if not done so yet
            djDeck.updateTrackInformation; // we first update the track description such that we have the most up to date information
            track = djDeck.track;
            trackTempo = track.bpm/60;
            userInducedGridOffsetStatic = track.userInducedGridOffset; // get the tracks stored userInducedGridOffset
            if(djDeck.clock.sync){ clock.activateSync(djDeck.clock.master) }{ clock.deactiveSync; clock.tempoInterface_(djDeck.clock.tempoInterface) }; // make sure the decks are synchronized in tempo
            clock.beats = djDeck.clock.beats; // we sync the beats, because we want it to play perfectly synced
            // if we have not loaded a track, the synth is paused, so we need to activate the synth after loading
            buffer = djDeck.buffer;
            trackBufferReady = true;
            this.reactivateSynth;
            synth.set(\trackTempo, trackTempo, \gain, track.preceivedDb.neg.dbamp); // we set the tempo, and the gain, where gain is chosen such that the track ends up at 0dB again
            (deckNr.asString++", loadDouble: \t"++track.artist++", "++track.title).log(this);
        }{
            "track is still playing on deck %".format(deckNr).log(this);
        }
    }

    loadAndPlayDouble { |newTrack|
        this.loadDouble(newTrack);
        this.play;
    }

    removeTrack {
        if(clock.paused){
            this.reset;
        }{
            "track is still playing on deck %".format(deckNr).log(this);
        }
    }

    // frontend: clock
    sync_ { |bool|
        if(bool){ clock.activateSync; "engage sync on deck %".format(deckNr).log(this); }{ clock.deactiveSync; "disengage sync on deck %".format(deckNr).log(this);  };
    }

    sync {
        ^clock.sync;
    }

    tempo_ { |newTempo|
        clock.tempoInterface_(newTempo);
    }

    tempo {
        ^clock.tempoInterface;
    }

    setAsMaster {
        clock.syncMasterToMe;
        "sync master on deck %".format(deckNr).log(this);
    }

    // frontend: playback
    pause {
        clock.pause;
        synth.set(\mute, 1); // if we only set the rate to zero, we will have that it keeps reading and outputing the same frame
    }

    play {
        if(trackBufferReady && track.isNil.not && (clock.beats < this.time2beatAdjusted(track.duration))){
            clock.resume;
            synth.set(\mute, 0); 
            if(endOfTrackEvent){ this.endOfTrackSched };
        }{"not ready".postln};
    }

    playPause {
        ^clock.paused.not;
    }

    playPause_ { |bool|
        if(bool){ this.play }{ this.pause };
    }

    que {
        if(clock.paused){
            if(this.beats == quePoint){
                this.playQue;
            }{
                this.setQue;
            }
        }{
            this.jumpToQue;
        }

    }

    pitchbend_ { |intensity = 1|
        synth.set(\bendEvent, 1.0.rand, \bendIntensity, intensity);
        intensity.postln;
    }

    needledropping_ { |relativePosition|
        var jumpToBeat;
        jumpToBeat = (track.duration * relativePosition * trackTempo).round;
        clock.beats_(jumpToBeat.min(this.time2beatAdjusted(track.duration))); // should go passed the end of the track
    }

    needledropping {
        ^(clock.beats / (track.duration * trackTempo));
    }

    beatJump { |beats, quant|
        var func, q, jumpAtBeat, goTo;
        q = quant ? beats.abs; 
        jumpAtBeat = q*(clock.beats/q).ceil;
        beatJumpBeats = beats.asInteger; // will be looked up at moment of jump by schedule jump (can be changed in the meantime)
        this.scheduleJump(jumpAtBeat);
    }

    loop_ { |bool|
        if(bool){
            if(schedJump){
                loop = true;
                this.beatJump(beatJumpBeats); // if we are not allready beatjumping, we active the beatJumping; in scheduleJump we check whether we have not yet allready scheduled something
            }
        }{
            loop = false;
        }
    }

    loopSize_ { |direction| 
        // direction should be a bool true = increase loopSize, false = decrease loopSize
        if(direction){ beatJumpBeats = (beatJumpBeats * 2).roundFractional(128).asFloat}{ if(beatJumpBeats.abs > (1/32)){ beatJumpBeats = (beatJumpBeats / 2).roundFractional(128).asFloat} };
        if(direction && beatJumpBeats == 1.0){ clock.phaseSync }; // if we come from a loop smaller than a beat, we make sure we phasesync to the master again, now our beats line up nicely (:
    }

    loopSize {
        ^beatJumpBeats;
    }

    // backend: controls
    setQue {
        quePoint = clock.beats.round;
    }

    playQue {
        this.play;
    }

    jumpToQue {
        this.pause;
        clock.beats_(quePoint);
    }

    // backend: other
    reset {
        this.update; // before removing the track we update the track description
        synth.run(false); // if we are not playing any track this synth is not active
        track = nil;
        trackBufferReady = false;
        if(buffer.dependants.size == 1){ buffer.free }{ buffer.removeDependant(this) }; // only free the buffer if I am the only dependant; see the .schelp for more thoughts on this
    }

    updateTrackInformation {
        // update track information based on current playback state
        track.userInducedGridOffset_(this.userInducedGridOffset); // we store the user induced gridoffset for usage next time
    }

    reactivateSynth {
        Server.default.makeBundle(nil,{
            synth.set(\bufnum, buffer); // we need to update to which buffers the synth is listening, because the buffer object is now referring to the buffer of the other DJdeck
            synth.run(true);
        });
    }

    beat2positon { |beat|
        ^(beat / trackTempo)* track.sampleRate;
    }

    position2beat { |position|
        ^((position / track.sampleRate) * trackTempo);
    }

    beat2positionAdjusted { |beat|
        ^(((beat + this.userInducedGridOffset) / trackTempo) + track.gridOffset) * track.sampleRate;
    }

    position2beatAdjusted { |position|
        ^(((position / track.sampleRate) - track.gridOffset) * trackTempo) - this.userInducedGridOffset;
    }

    time2beatAdjusted { |time|
        ^((time - track.gridOffset) * trackTempo) - this.userInducedGridOffset;
    }

    updateUserInducedGridOffsetStatic {
        // this function is only valid is you use it while doing a beatjump, because this resets the current difference between playAlong and refernce buffer playback to zero
        userInducedGridOffsetStatic = userInducedGridOffsetTotal;
    }
    
    userInducedGridOffset {
        // in beats; per definition: //^userInducedGridOffset = this.beatsPlayAlong - clock.beats;
        // at every beatjump we set all buffers to the same frame; when pitchbending occurs the track deck and the playalong change relative to the reference track (this is the dynamic part), when we recalculate the userInducedGridOffset, we add the current difference between play along and reference (the dynamic part) to the allready know userInducedGridOffset (the static part)
        var positionPlayAlong, positionReference, newUserInducedGridOffset;
        #positionPlayAlong, positionReference = referenceBus.getnSynchronous(2);
        newUserInducedGridOffset = this.position2beat(positionPlayAlong - positionReference);
        ^userInducedGridOffsetTotal = userInducedGridOffsetStatic + newUserInducedGridOffset;
    }

    beatsReference {
        ^this.position2beatAdjusted(referenceBus.getSynchronous);
    }

    ready {
        ^(trackBufferReady);
    }

    scheduleJump { |jumpAtBeat|
        if(schedJump.not && (jumpAtBeat <= this.time2beatAdjusted(track.duration))){
            clock.schedAbs(jumpAtBeat, {
                clock.beats_((clock.beats + beatJumpBeats)); // we modify the clock's beats, the track follows via the .update callback
                schedJump = false;
                if(loop){ SystemClock.sched(0.1,{ this.scheduleJump(jumpAtBeat) }) }; // we need to schedule this a bit later in order to deal with the fact that logical time remains constant during a scheduled task;
            });
            schedJump = true;
        };
    }

    endOfTrackSched {
        // when reaching the end of the track: pause (should be conditionally rescheduled every time we press play)
        clock.schedAbs(this.time2beatAdjusted(track.duration), {
            endOfTrackEvent = true;
            this.pause;
        });
    }

    jumpToBeat { |beat|
        // let the clock jump to a specific beat and make sure track follows; this function is called via .update; the clock is leading
        var position;
        this.userInducedGridOffset; // here we calculate and store the current total userInducedGridOffset
        position = this.beat2positionAdjusted(beat);
        if(position >= 0){ 
            this.jumpToPosition(position);
        }{ // if the position is lower than zero, there is nothing in the buffer to play, so we pause the synth temporarily, and activate when we arrive at the start of the file
            pauseBus.setSynchronous(1);
            //synth.run(false); // alternatively we instead could define a paused argument for the synth; if this is high, then the rate will be set to zero
            this.jumpToPosition(0);            
            clock.schedAbs(this.position2beatAdjusted(0), {pauseBus.setSynchronous(0) });
        }
    }

    jumpToPosition { |position|
        // make a track on the synth resume playing from position in frames
        // we use busses to obtain synchronicity between lang and server; we write a random number to the jumpToPositionNumber because this is the way I saw to transmit discrete signal to the server (in combination with a Changed UGen)
        playerSelected = playerSelected.not; // we switch to the other player
        positionSetBus.setSynchronous(position);
        playerSelectionBus.setSynchronous(playerSelected.asInteger);
        this.updateUserInducedGridOffsetStatic; // after jumpting the reference and playalong bus are reset, hence we also need to reset/restore/update the userInducedGridOffset
    }

    spawnSynths { |target, addAction|
        synth = Synth.newPaused("DJdeck", [\outputBus, bus, \bufnum, buffer, \deckTempoBus, clock.bus, \trackTempo, trackTempo, \referenceBus, referenceBus, \positionSetBus, positionSetBus, \playerSelectionBus, playerSelectionBus, \pauseBus, pauseBus], target, addAction);
    }

    update { |theChanged, theChanger|
        var theChangerKey, theChangerValue;
   
        if(theChanger.class == Array){ 
            # theChangerKey, theChangerValue = theChanger;
            if(theChangerKey == \beats){
                // this links a jump in beats on the clock to a jump in the playing track/buffer
                // we can't use the clock.beats value here, since this value remains unchanged during a scheduled function call, since this freezes the logical time
                this.jumpToBeat(theChangerValue);
                //theChangerValue.postln;
                // theChanged.beats.postln;
            }
        }
    }

    *addSynthDef {
        // SynthDef classes
        Class.initClassTree(SynthDef);
        Class.initClassTree(SynthDescLib);
        
        // used UGens
        Class.initClassTree(Changed);
        Class.initClassTree(Lag);
        Class.initClassTree(BufRateScale);
        Class.initClassTree(In);
        Class.initClassTree(K2A);
        Class.initClassTree(SampleRate);
        Class.initClassTree(Sweep);
        Class.initClassTree(BufRd);
        Class.initClassTree(Out);
        Class.initClassTree(DeckPlayer);

        // synthdef itself
        SynthDef(\DJdeck, { |bufnum, outputBus = 0, referenceBufnum, referenceBus, mute = 0, positionSetBus, playerSelectionBus, trackTempo, deckTempoBus, bendEvent, bendIntensity, pauseBus, gain|
            var rate, rateBended, trig, output, positionInput, position, blockPosition, referencePosition, blockReferencePosition, playerSelection;
            var bend;
            var outputBundledP1, outputP1, positionP1, referencePositionP1;
            var outputBundledP2, outputP2, positionP2, referencePositionP2;

            // pitch bending
            bend = Changed.kr(bendEvent);
            bend = bend * bendIntensity;
            bend = Lag.kr(bend, 0.4);

            // rate
            rate =  BufRateScale.kr(bufnum) * (In.kr(deckTempoBus) / trackTempo) * (1 - In.kr(pauseBus)); // if it is paused the rate will be zero; paused is to be used only for when the beat is negative, meaning we are before the track, so we need to wait with playing the track untill we arrive at the start of the track
            rateBended = rate + bend;

            // jumping
            playerSelection = In.kr(playerSelectionBus); // we switch from player - with a short crossfade - when we do a beatjump
            positionInput = In.kr(positionSetBus);

            // players
            # outputP1, positionP1, referencePositionP1 = DeckPlayer.ar(playerSelection, bufnum, positionInput, rate, rateBended);
            # outputP2, positionP2, referencePositionP2 = DeckPlayer.ar((1 - playerSelection), bufnum, positionInput, rate, rateBended);

            // player addition
            output = outputP1 + outputP2; // fading is integrated in the players themselves
            position = (playerSelection * positionP1) + ((1 - playerSelection) * positionP2); // select position in accordance with the selected player
            referencePosition = (playerSelection * referencePositionP1) + ((1 - playerSelection) * referencePositionP2);

            // output
            output = output * gain;
            output = output * Lag.kr(1 - mute, 0.01);
            Out.ar(outputBus, output);

            // for reference
            Out.kr(referenceBus, [position, referencePosition]);
        }).writeDefFile;
    }
}

DeckPlayer {
    *ar { |gate, bufnum, positionOffset, rate, rateBended|
            var env, trig, position, positionOffsetLatched, referencePosition, output;

            // trigger
            trig = T2A.ar(PositiveEdge.kr(gate));

            // position
            positionOffsetLatched = Latch.kr(positionOffset, trig); // we only set the positionOffset upon switching to this player
            position = positionOffsetLatched + (SampleRate.ir * Sweep.ar(trig, rateBended));
            referencePosition = positionOffsetLatched + (SampleRate.ir * Sweep.ar(trig, rate));

            // actual soundfile reading
            output = BufRd.ar(2, bufnum, position);

            // envelop
            output = output * Linen.kr(gate, attackTime: 0.001, releaseTime: 0.001);

            // return
            ^[output, position, referencePosition];
    }
}