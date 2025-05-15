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
    var <deckNr;
    var <bus, <clock, <buffer, <synth, <referenceBus, <track;
    var <quePoint = 0, <jumpTask, <schedJump = false, <loop = false, <beatJumpBeats = -4, <jumpAtBeat, loopTable, <loopTableIndex = 9;
    var <trackBufferReady = false;
    var testBus, testBuffer;
    var <positionSetBus, <playerSelectionBus;
    var <playerSelected = false;
    var <endOfTrackEvent = true; // this makes sure that we prepare for the endOfTrack initially
    var <scratchEventNumber = 0;
    var <oscListener;

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
        referenceBus = Bus.control(numChannels: 3);
        positionSetBus = Bus.control;
        playerSelectionBus = Bus.control; // initialized to 0, which corresponds with playerSelected = false
        buffer = Buffer.loadCollection(Server.default,[0, 0, 0, 0], 2, action: {this.spawnSynths(target, addAction)} ); // use .loadCollection instead of .alloc because it allows to supply an action function; after the buffer is setup, we create the synth
        // ideally I would free the buffer here, but then the buffer will be free before the synth is created
        track = TrackDescription.newDummy(125, 6); // placeholder
        loopTable = [1/128.neg, 1/64.neg, 1/32.neg, 1/16.neg, 1/8.neg, 1/4.neg, 1/2.neg, 1.neg, 2.neg, 4.neg, 8.neg, 16.neg, 32.neg];
        oscListener = OSCFunc({ |msg| 
            if(msg[1] == synth.nodeID){
                this.align;
            };
        },'/tr', Server.default.addr);
        jumpTask = DynamicTask.new(clock);
    }

    // frontend: tracks
    loadTrack { |newTrack, action|
        if(clock.paused){ // we only load a track if no track is allready playing
            if(track.title.isNil.not){ this.reset }; // reset the deck if not done so yet
            track = newTrack;
            if(clock.sync.not){ clock.tempoInterface_((track.bpm/60)) }; // play track at normal rate if not synced
            //setpointGridOffset = track.userInducedGridOffset; // get the tracks stored userInducedGridOffset
            clock.beats(this.position2beat(0));
            // if we have not loaded a track, the synth is paused, so we need to activate the synth after loading
            buffer = track.loadBuffer(action: { trackBufferReady = true; this.reactivateSynth; action.value });
            synth.set(\trackTempo, (track.bpm/60), \gain, track.preceivedDb.neg.dbamp);  // we set the tempo, and the gain, where gain is chosen such that the track ends up at 0dB again
            (deckNr.asString++", loadTrack: \t"++track.artist++", "++track.title).log(this);
            this.endOfTrackSched; // this paused the track when reaching the end
            ^true;
        }{
            (deckNr.asString++", track is still playing").log(this);
            ^false;
        }
    }

    loadDouble { |djDeck| //not working yet
        // should double the song allready playing on the deck, this is also the way we load songs: we copy them from the prelistenDeck
        if(clock.paused){ // we only load a track if no track is allready playing
            if(track.title.isNil.not){ this.reset }; // reset the deck if not done so yet
            track = djDeck.track;
            //setpointGridOffset = track.userInducedGridOffset; // get the tracks stored userInducedGridOffset
            if(djDeck.clock.sync){ clock.activateSync(djDeck.clock.master) }{ clock.deactiveSync; clock.tempoInterface_(djDeck.clock.tempoInterface) }; // make sure the decks are synchronized in tempo
            buffer = djDeck.buffer;
            trackBufferReady = true;
            if(djDeck.playPause){ this.play };
            clock.beats_(djDeck.clock.beats); // we sync the beats, because we want it to play perfectly synced
            this.reactivateSynth;
            synth.set(\trackTempo, (track.bpm/60), \gain, track.preceivedDb.neg.dbamp); // we set the tempo, and the gain, where gain is chosen such that the track ends up at 0dB again
            (deckNr.asString++", loadDouble: \t"++track.artist++", "++track.title).log(this);
            this.endOfTrackSched;
            ^true;
        }{
            (deckNr.asString++", track is still playing").log(this);
            ^false;
        }
    }    

    loadTrackFromBuffer{ |newTrack, newBuffer|
        if(clock.paused){ // we only load a track if no track is allready playing
            if(track.title.isNil.not){ this.reset }; // reset the deck if not done so yet
            track = newTrack;
            if(clock.sync.not){ clock.tempoInterface_((track.bpm/60)) }; // play track at normal rate if not synced
            //setpointGridOffset = track.userInducedGridOffset; // get the tracks stored userInducedGridOffset
            clock.beats_(this.position2beat(0));
            buffer = newBuffer;
            trackBufferReady = true;
            this.reactivateSynth;
            synth.set(\trackTempo, (track.bpm/60), \gain, track.preceivedDb.neg.dbamp);  // we set the tempo, and the gain, where gain is chosen such that the track ends up at 0dB again
            (deckNr.asString++", loadTrack: \t"++track.artist++", "++track.title).log(this);
            this.endOfTrackSched;
            
            ^true;
        }{
            (deckNr.asString++", track is still playing").log(this);
            ^false;
        }
    }
    
    loadAndPlayTrack { |newTrack|
        this.loadTrack(newTrack, { this.play } );
    }

    loadAndPlayDouble { |newTrack|
        this.loadDouble(newTrack);
        this.play;
    }

    removeTrack {
        if(clock.paused){
            this.reset;
        }{
            (deckNr.asString++", track is still playing").log(this);
        }
    }

    resetTrack {
        // the user can override the userInducedGridOffset, using resetTrack, the user can restore the track to its original settings;
        track.userInducedGridOffset = track.gridOffset;
    }

    // frontend: clock
    sync_ { |bool|
        if(bool){ clock.activateSync; (deckNr.asString++", engage sync").log(this);}{ clock.deactiveSync; "disengage sync on deck %".format(deckNr).log(this);  };
        this.changed(\sync);
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
        //synth.set(\mute, 1); // if we only set the rate to zero, we will have that it keeps reading and outputing the same frame
    }

    play {
        if(trackBufferReady && track.title.isNil.not){ // check if track is properly loaded and ready
            if(clock.beats < this.time2beat(track.duration)){ // check if not superseded end of treck
                if(clock.sync){ clock.phaseSync }{ this.align };
                clock.resume;
                //synth.set(\mute, 0); 
                if(endOfTrackEvent){ this.endOfTrackSched };
            }{"can't play, because at end of track".log(this)}
        }{"can't play, because track is not ready".log(this)};
    }

    playPause {
        ^clock.paused.not;
    }

    playPause_ { |bool|
        if(bool){ this.play }{ this.pause };
    }

    que {
        if(clock.paused){
            if(this.clock.beats == quePoint){
                this.playQue;
            }{
                this.setQueSnap;
            }
        }{
            this.jumpToQue;
        }
    }

    queShift {
        if(clock.paused){
            if(this.clock.beats == quePoint){
                this.playQue;
            }{
                this.setQue;
            }
        }{
            this.jumpToQue;
        }
    }

    phraseQue {
        // similar to que, but now set que point at nearest beginning of a phrase, rounded down
        if(clock.paused){
            if(this.clock.beats == quePoint){
                this.playQue;
            }{  
                clock.beats_(clock.phrase.floor * clock.barsPerPhrase * clock.beatsPerBar);
                this.setQue;
            }
        }{
            this.jumpToQue;
        }
    }

    pitchbend_ { |intensity = 1|
        if(clock.paused.not){
            synth.set(\bendEvent, 1.0.rand, \bendIntensity, intensity);
        }
    }

    scratch_ { |intensity = 1|
        if(clock.paused){ //K2A.ar(scratchEventNumber * BufSampleRate.kr(bufnum) * 2 / 180);
            scratchEventNumber = scratchEventNumber + (track.sampleRate * 2 / 180 * intensity); // from MidiOX analysis it becomes apparent that the platter gives 180 pulses if turned slowly, which are being grouped together iw they are spaced within 25ms
            synth.set(\scratchEventNumber, scratchEventNumber);
        }
    }

    needledropping_ { |relativePosition|
        var jumpToBeat;
        jumpToBeat = (track.duration * relativePosition * (track.bpm/60)).round;
        clock.beats_(jumpToBeat); // should go passed the end of the track
        this.changed(\needledropping);
    }

    needledropping {
        ^this.relativePosition;
    }

    relativePosition {
        ^(clock.beats / (track.duration * (track.bpm/60)));
    }

    beatJumpNow { |beats|
        clock.beats_(clock.beats + beats);
    }

    beatJumpShifted { |beats, quant|
        if(clock.paused){
            this.beatJumpQuantized(beats, quant);
        }{
            this.beatJumpScheduled(beats, quant);
        }
    }

    // frontend: loop
    loop_ { |bool|
        if(bool){
            loop = true;
            if(jumpTask.cancel){
                this.beatJumpScheduled; // if we are not allready beatjumping, we active the beatJumping; in scheduleJump we check whether we have not yet allready scheduled something
            };
        }{
            jumpTask.cancel_;
            loop = false;
        };
        this.changed(\loop);
    }

    loopTableIndex_ { |val| 
        // TODO: change approach: work with a lookup table
        // direction should be a bool true = increase loopSize, false = decrease loopSize
        var previousBeatJumpBeats;
        loopTableIndex = val.clip(0,loopTable.size-1);
        previousBeatJumpBeats = beatJumpBeats;
        beatJumpBeats = loopTable[loopTableIndex];
        if(beatJumpBeats == 1.0){ clock.phaseSync }; // if we come from a loop smaller than a beat, we make sure we phasesync to the master again, now our beats line up nicely (:

        // front looping (see ppt), we also need to alter the point at which we jump
        if(loop){ 
            jumpAtBeat = jumpTask.schedAtTime + previousBeatJumpBeats - beatJumpBeats;
            jumpTask.schedAbs(jumpAtBeat); 
        };
    }

    loopSize {
        ^beatJumpBeats;
    }

    // backend: que
    setQueSnap {
        quePoint = clock.beats.round;
    }

    setQue {
        this.align; // first make sure the clock and track are indeed aligned;
        this.userInducedGridOffset_(this.beat2gridOffset(clock.beats.floor, clock.beats));
        quePoint = clock.beats; // since we have now updated the gridOffset such that the current position is a whole beat, and aligned, the quepoint is a whole beat
    }

    playQue {
        this.play;
    }

    jumpToQue {
        this.pause;
        clock.beats_(quePoint);
    }

    // backend: conversion
    beat2time { |beat|
        ^(beat / (track.bpm/60)) + track.userInducedGridOffset
    }

    time2beat { |time|
        ^(time - track.userInducedGridOffset) * (track.bpm/60);
    }

    time2position { |time|
        ^time * track.sampleRate;
    }

    position2time { |position|
        ^position / track.sampleRate
    }

    beat2position { |beat|
        ^this.time2position(this.beat2time(beat));
    }

    position2beat { |position|
        ^this.time2beat(this.position2time(position));
    }

    beat2gridOffset { |beatNew, beatOld|
        // see DJdeck ppt
        var tempGridOffset;
        // we get rid of any whole number of beats difference, which will be there if we compare to the master clock
        if(beatNew.frac <= beatOld.frac){
            beatNew = beatOld.floor + 1 + beatNew.frac;
        }{
            beatNew = beatOld.floor + beatNew.frac;
        };
        tempGridOffset = ((beatOld - beatNew) / (track.bpm/60)) + track.userInducedGridOffset;
        if(tempGridOffset < 0){ // if so, we should decrease the newBeat value by one, such that the grid moves to the right
            tempGridOffset = ((beatOld - beatNew + 1) / (track.bpm/60)) + track.userInducedGridOffset;
        };
        ^tempGridOffset;
    }

    // backend: alignment
    align {
        // align buffer playback position and trackclock (they get out of sync due to pitchshifting, and scratching), by setting the clock to the actual playback position
        var position, referencePosition, positionNotClipped;
        # position, referencePosition, positionNotClipped = referenceBus.getnSynchronous(3);
        clock.beatsNoJump_(this.position2beat(positionNotClipped));
        (deckNr.asString++", trackClock aligned").log(this);
    }

    userInducedGridOffset_ { |value|
        track.userInducedGridOffset = value;
        this.align; // since we have now changed the shifted the grid, that is we have changed the clock relative to the track, we should align the clock again, so that it corresponds again to the track
    }

    overwriteGridOffsetFromMaster {
        this.align; // first make sure the clock and track are indeed aligned;
        this.userInducedGridOffset_(this.beat2gridOffset(clock.master.beats, clock.beats));
    }

    // backend: beatJumping 
    beatJumpScheduled { |beats, quant|
        // scheduled and quatized jump
        var func, q, goTo;
        if(beats.isNil.not){ beatJumpBeats = beats };
        q = quant ? beatJumpBeats; 
        q = q.abs;
        jumpAtBeat = q*((clock.beats/q).floor + 1);
        //beatJumpBeats = beats.asInteger; // will be looked up at moment of jump by schedule jump (can be changed in the meantime)
        this.scheduleJump;
    }

    beatJumpQuantized { |beats, quant|
        var currentBeat, q;
        q = quant ? beats; 
        q = q.abs;
        currentBeat = clock.beats;
        if((currentBeat / q).frac < 0.1){ // if we are close to a quantized point, we jump to the next point
            clock.beats_(((currentBeat + beats)/q).round * q);
        }{ // if we are not close we jump to the first quantized point in the correct direction
            if(beats > 0){
                clock.beats_((currentBeat/q).ceil * q);
            }{
                clock.beats_((currentBeat/q).floor * q);
            }
        }
    }

    scheduleJump {
        if(jumpTask.cancel && (jumpAtBeat <= this.time2beat(track.duration))){
            jumpTask.target_({
                clock.beats_((clock.beats + beatJumpBeats)); // we modify the clock's beats, the track follows via the .update callback
                if(loop){ SystemClock.sched(0.1,{ this.scheduleJump(jumpAtBeat) }) }; // we need to schedule this a bit later in order to deal with the fact that logical time remains constant during a scheduled task;
            });
            jumpTask.schedAbs(jumpAtBeat);
        };
    }

    /* scheduleJump { |jumpAtBeat|
        if(schedJump.not && (jumpAtBeat <= this.time2beat(track.duration))){
            clock.schedAbs(jumpAtBeat, {
                clock.beats_((clock.beats + beatJumpBeats)); // we modify the clock's beats, the track follows via the .update callback
                schedJump = false;
                if(loop){ SystemClock.sched(0.1,{ this.scheduleJump(jumpAtBeat) }) }; // we need to schedule this a bit later in order to deal with the fact that logical time remains constant during a scheduled task;
            });
            schedJump = true;
        };
    } */

    jumpToBeat { |beat|
        // let the clock jump to a specific beat and make sure track follows; this function is called via .update; the clock is leading
        var position;
        position = this.beat2position(beat);
        scratchEventNumber = 0;
        synth.set(\scratchEventNumber, scratchEventNumber);
        this.jumpToPosition(position);
    }

    jumpToPosition { |position|
        // make a track on the synth resume playing from position in frames
        // we use busses to obtain synchronicity between lang and server; we write a random number to the jumpToPositionNumber because this is the way I saw to transmit discrete signal to the server (in combination with a Changed UGen)
        playerSelected = playerSelected.not; // we switch to the other player
        positionSetBus.setSynchronous(position);
        playerSelectionBus.setSynchronous(playerSelected.asInteger);
    }

    // backend: other
    reset {
        //this.update; // before removing the track we update the track description //TODO: shouldn't this be "updateTrackInformation" ?
        synth.run(false); // if we are not playing any track this synth is not active
        track = TrackDescription.newDummy(125, 6); // placeholder;
        trackBufferReady = false;
        if(buffer.dependants.size == 1){ buffer.free }{ buffer.removeDependant(this) }; // only free the buffer if I am the only dependant; see the .schelp for more thoughts on this
        this.loop_(false);
        this.loopTableIndex_(9);
        quePoint = 0;
        scratchEventNumber = 0;
        referenceBus.setnSynchronous([0, 0, 0]);
        //track.userInducedGridOffset = setpointGridOffset; // store userInducedGridOffset in the track
    }

    reactivateSynth {
        Server.default.makeBundle(nil,{
            synth.set(\bufnum, buffer); // we need to update to which buffers the synth is listening, because the buffer object is now referring to the buffer of the other DJdeck
            synth.run(true);
        });
    }

    ready {
        ^(trackBufferReady);
    }

    endOfTrackSched {
        // TODO should be implemented using DynamicTask, because should be changed upon loading a new track, since new track has different end point
        // currently disabled because not needed
        // when reaching the end of the track: pause (should be conditionally rescheduled every time we press play)
        //clock.schedAbs(this.time2beat(track.duration), {
        //    endOfTrackEvent = true;
        //    "endOfTrackEvent".log(this);
        //    // this.pause;
        //});
        //endOfTrackEvent = false; // we have taken action upon the endOfTrackEvent, so we can now reset it
    }

    spawnSynths { |target, addAction|
        synth = Synth.newPaused("DJdeck", [\outputBus, bus, \bufnum, buffer, \deckTempoBus, clock.bus, \trackTempo, track.bpm/60, \referenceBus, referenceBus, \positionSetBus, positionSetBus, \playerSelectionBus, playerSelectionBus], target, addAction);
    }

    info {
        "\tPlaying:\t%\n".postf(clock.paused.not);
        "\tSync:\t%\n".postf(clock.sync);
        "\tBPM:\t%\n".postf(clock.tempo * 60);
        "\tBeat:\t%\n".postf(clock.beats);
        "\tTime:\t%\n".postf(this.beat2time(clock.beats));
        "\tDuration:\t%\n".postf(track.duration);
        "\tBeatJumpBeats:\t%\n".postf(beatJumpBeats);
        "\tJumpAtBeat:\t%\n".postf(jumpAtBeat);
        "\n".postf();
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
            };
        };
        if(theChanger == \sync){
            this.changed(\sync);
        };
        if(theChanger == \playPause){
            this.changed(\playPause);
        };
    }

    // synthdef
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
        SynthDef(\DJdeck, { |bufnum, outputBus = 0, referenceBufnum, referenceBus, mute = 0, positionSetBus, playerSelectionBus, trackTempo, deckTempoBus, bendEvent, bendIntensity, gain, scratchEventNumber, scratchIntensity, scopeBusKr, scopeBusAr|
            var rate, rateBended, trig, output, positionInputIn, positionInput, position, positionNotClipped, blockPosition, referencePosition, blockReferencePosition, playerSelectionIn, playerSelection;
            var bendTrig, bend;
            var outputBundledP1, outputP1, positionP1, positionNotClippedP1, referencePositionP1;
            var outputBundledP2, outputP2, positionP2, positionNotClippedP2, referencePositionP2;
            var scratchTrig, scratchEvent, scratchEstimatedTime, scratchPosition, scratchPositionFiltered, targetScratchPosition, previousScratchPosition, scratchRate, resetScratch;
            var trackClockTrig;

            // pitch bending
            bendTrig = Changed.kr(bendEvent);
            bend = bendTrig * bendIntensity;
            bend = Lag.kr(bend, 0.25);

            // rate
            rate = In.kr(deckTempoBus) / trackTempo;
            rateBended = rate + bend;

            // jumping
            playerSelection = K2Adiscrete.ar(In.kr(playerSelectionBus));
            positionInput = K2Adiscrete.ar(In.kr(positionSetBus));

            // scratchPosition
            scratchTrig = Changed.kr(scratchEventNumber); //using .ar directly does not work
            targetScratchPosition = K2A.ar(scratchEventNumber);
            scratchEstimatedTime = 0.025; 
            previousScratchPosition = LocalIn.ar();
            scratchRate = Latch.ar((targetScratchPosition - previousScratchPosition).abs / scratchEstimatedTime, K2A.ar(scratchTrig));
            resetScratch = InRange.ar(targetScratchPosition, -0.5, 0.5);
            scratchRate = (resetScratch * 100000000000) + ((1 - resetScratch) * scratchRate);
            //scratchRate = Select.ar(LFSaw.ar(1.0,0.0,1,1), [scratchRate, 100000000000]); // if targetScratchPosition = 0, then set scratchRate to inf, in order te reach immediate, as a means of resetting upon beat jumping
            scratchPosition = Slew.ar(targetScratchPosition, scratchRate, scratchRate);
            scratchPositionFiltered = Lag.ar(in: scratchPosition, lagTime: 0.1);
            LocalOut.ar(scratchPosition);

            // tell language to sync trackClock
            trackClockTrig = Sweep.kr(bendTrig + (scratchTrig * (1 - resetScratch)), rate: 1.0) > 0.25; // if more than 0.5s no bend or scratch, then align clock, see OSC listener as defined in init
            SendTrig.kr(trackClockTrig);

            // players
            # outputP1, positionP1, positionNotClippedP1, referencePositionP1 = DeckPlayer.ar(playerSelection, bufnum, positionInput, rate, rateBended, scratchPositionFiltered);
            # outputP2, positionP2, positionNotClippedP2, referencePositionP2 = DeckPlayer.ar((1 - playerSelection), bufnum, positionInput, rate, rateBended, scratchPositionFiltered);

            // player addition
            output = outputP1 + outputP2; // fading is integrated in the players themselves
            position = (playerSelection * positionP1) + ((1 - playerSelection) * positionP2); // select position in accordance with the selected player
            positionNotClipped = (playerSelection * positionNotClippedP1) + ((1 - playerSelection) * positionNotClippedP2);
            referencePosition = (playerSelection * referencePositionP1) + ((1 - playerSelection) * referencePositionP2);

            // output
            output = output * gain;
            output = output * Lag.kr(1 - mute, 0.01);
            Out.ar(outputBus, output);

            // for reference
            Out.kr(referenceBus, [A2Kcentered.kr(position), referencePosition, positionNotClipped]);
        }).writeDefFile;
    }

    // depreciated
    /*     beat2positon { |beat|
        ^((beat / trackTempo) + track.userInducedGridOffset) * track.sampleRate;
    }

    position2beat { |position|
       
    }

    beat2positionAdjusted { |beat|
        ^(((beat + setpointGridOffset) / trackTempo) + track.gridOffset) * track.sampleRate;
    }

    position2beatAdjusted { |position|
        ^(((position / track.sampleRate) - track.gridOffset) * trackTempo) - setpointGridOffset;
    }

    time2beatAdjusted { |time|
        ^((time - track.gridOffset) * trackTempo) - setpointGridOffset;
    } 
        updateUserInducedGridOffsetStatic {
        // this function is only valid is you use it while doing a beatjump, because this resets the current difference between playAlong and refernce buffer playback to zero
        userInducedGridOffsetStatic = setpointGridOffset;
    }
    
    beatsReference {
        ^this.position2beatAdjusted(referenceBus.getSynchronous);
    }
    
    inducedGridOffset {
        // in beats; per definition: //^userInducedGridOffset = this.beatsPlayAlong - clock.beats;
        // at every beatjump we set all buffers to the same frame; when pitchbending occurs the track deck and the playalong change relative to the reference track (this is the dynamic part), when we recalculate the userInducedGridOffset, we add the current difference between play along and reference (the dynamic part) to the allready know userInducedGridOffset (the static part)
        var positionPlayAlong, positionReference, newUserInducedGridOffset;
        #positionPlayAlong, positionReference = referenceBus.getnSynchronous(2);
        ^newUserInducedGridOffset = this.position2beat(positionPlayAlong - positionReference);
    }

    setpointGridOffset_ {
        // here we store the user induced gridoffset as the setpoint gridoffset; so you can use this to phase align the grid with the track, if you are not satisfied with the supplied grids phase alignment
        setpointGridOffset = this.inducedGridOffset;
    }
    */
}

DeckPlayer {
    *ar { |gate, bufnum, positionOffset, rate, rateBended, scratchPosition|
            var env, trig, position, positionNotClipped, positionOffsetLatched, referencePosition, output;

            // trigger
            //trig = T2A.ar(PositiveEdge.kr(gate));
            trig = PositiveEdge.ar(gate);

            // position
            positionOffsetLatched = Latch.ar(positionOffset, trig); // we only set the positionOffset upon switching to this player
            positionNotClipped = positionOffsetLatched + (BufSampleRate.kr(bufnum) * Sweep.ar(trig, rateBended)) + scratchPosition; //12-02  not sure why this works, since the sweep is never resetted (this is not possible)
            position = Clip.ar(positionNotClipped, 0, BufFrames.kr(bufnum)); 
            referencePosition = positionOffsetLatched + (BufSampleRate.kr(bufnum)* Sweep.ar(trig, rate));

            // actual soundfile reading
            output = BufRd.ar(2, bufnum, position);
            output = output * K2A.ar(Changed.kr(A2K.kr(output))); // this makes sure output returns to zero when playback stops, so we do not keep outputting the value of the last frame

            // envelop
            output = output * Linen.kr(gate, attackTime: 0.05, releaseTime: 0.05);

            // return
            ^[output, position, positionNotClipped, referencePosition];
    }
}