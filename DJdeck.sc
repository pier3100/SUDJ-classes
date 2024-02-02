/* 
TODO
- keeping track of current play position:
    - append the track with a third channel which contains a ramp (not that easy to accomplisch as buffers can't be merged)
    - similar as above but use a standard ramp buffer, and read it in parallel (analyze whether these buffers are always synced)
    - use sendID of VDiskIn

NICE TO HAVE
- get rid of popping when beatjumping
- reduce loading time of songs, make a function to preview songs which uses VDiskIn, which requires less buffer to be loaded as compared to ReadBuf


DONE
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
*/

DJdeck : Object {
    classvar frameTrackerArray;
    var <bus, <clock, <buffer, <synth, <referenceBuffer, <referenceBus, <track;
    var trackTempo, quePoint = 0, schedJump = false, <>loop = false, loopLength = 4, needledroppingRelativePosition = 0;
    var <referenceBufferReady = false, <trackBufferReady = false;
    var testBus, testBuffer;
    var userInducedGridOffsetTotal = 0, <userInducedGridOffsetStatic = 0;
    var <positionSetBus, <jumpToPositionBus;

    *new { |bus, bpm|
        ^super.new.init(bus, bpm);
    }

    init { |b, bpm = 125|
        bus = b;
        clock = MyTempoClock(bpm/60).pause;
        referenceBus = Bus.control(numChannels: 2);
        positionSetBus = Bus.control;
        jumpToPositionBus = Bus.control;
        buffer = Buffer.alloc(Server.default, 100, 2);
        referenceBuffer = Buffer.alloc(Server.default, 100, 1);
        this.spawnSynths;
    }

    *classInit {
        var typicalSampleRate = 44100, typicalDuration = 600;
        Class.initClassTree(Array);
        frameTrackerArray = Array.series((typicalDuration*typicalSampleRate).floor.asInteger, 1, 1);
    }

    loadTrack { |newTrack, action|
        this.reset;
        track = newTrack;
        trackTempo = track.bpm/60;
        clock.beats = this.position2beatAdjusted(0);
        buffer = track.loadBuffer(action: { trackBufferReady = true; action.value });
        if(track.numFrames > frameTrackerArray.size){ frameTrackerArray = frameTrackerArray ++ Array.series(track.numFrames - frameTrackerArray.size + 1, frameTrackerArray.size + 1, 1) };
        referenceBuffer = Buffer.loadCollection(collection: frameTrackerArray[0..track.numFrames], action: { referenceBufferReady = true; action.value });
        synth.set(\trackTempo, trackTempo); 
    }

    loadAndPlayTrack { |newTrack|
        this.loadTrack(newTrack, { this.play } );
    }

    reset {
        track = nil;
        referenceBufferReady = false;
        trackBufferReady = false;
        buffer.free;
        referenceBuffer.free;
    }

    beat2positon { |beat|
        ^(beat / trackTempo)* track.sampleRate;
    }

    position2beat { |position|
        ^((position / track.sampleRate)* trackTempo);
    }

    beat2positionAdjusted { |beat|
        ^(((beat + this.userInducedGridOffset) / trackTempo) + track.gridOffset) * track.sampleRate;
    }

    position2beatAdjusted { |position|
        ^(((position / track.sampleRate) - track.gridOffset) * trackTempo) - this.userInducedGridOffset;
    }

    updateUserInducedGridOffsetStatic {
        userInducedGridOffsetStatic = userInducedGridOffsetTotal;
    }
    
    userInducedGridOffset {
        // in beats; per definition: //^userInducedGridOffset = this.beatsPlayAlong - clock.beats;
        // at every beatjump we set all buffers to the same frame; when pitchbending occurs the track deck and the playalong change relative to the reference track, when we recalculate the userInducedGridOffset, we add the current difference between play along and reference to the allready know userInducedGridOffset
        // this function is only valid is you use it while doing a beatjump, because this resets the current difference between playAlong and refernce buffer playback to zero
        var positionPlayAlong, positionReference, newUserInducedGridOffset;
        #positionPlayAlong, positionReference = referenceBus.getnSynchronous(2);
        newUserInducedGridOffset = this.position2beat(positionPlayAlong - positionReference);
        ^userInducedGridOffsetTotal = userInducedGridOffsetStatic + newUserInducedGridOffset;
    }

    beatsReference {
        ^this.position2beat(referenceBus.getSynchronous);
    }

    pause {
        clock.pause;
    }

    play {
        if(this.ready){
            clock.resume;
            synth.run(true);
        }{"not ready".postln};
    }

    ready {
        ^(referenceBufferReady && trackBufferReady);
    }

    setQue {
        quePoint = clock.beats.round;
    }

    playQue {
        this.play;
    }

    jumpToQue {
        this.pause;
        this.jumpToBeat(quePoint);
    }

    que {
        if(clock.paused){
            if(this.beats = quePoint){
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
    }

    needledropping_ { |relativePosition|
        var jumpToBeat;
        needledroppingRelativePosition = relativePosition;
        jumpToBeat = (track.duration * relativePosition * clock.tempo).round;
        this.jumpToBeat(jumpToBeat)
    }

    needledropping {
        ^needledroppingRelativePosition;
    }

    beatJump { |beats, quant|
        var func, q, jumpAtBeat, jumpToBeat, goTo;
        q = quant ? beats.abs; 
        jumpAtBeat = q*(clock.beats/q).ceil;
        this.scheduleJump(beats,jumpAtBeat);
    }

    scheduleJump { |beats, jumpAtBeat|
        if(schedJump.not){
            clock.schedAbs(jumpAtBeat,{
                this.jumpToBeat(clock.beats+beats);
                schedJump = false;
                if(loop){ SystemClock.sched(0.1,{ this.scheduleJump(beats,jumpAtBeat) }) };
            });
            schedJump = true;
        };
    }

    jumpToBeat { |beat|
        var position;
        this.userInducedGridOffset;
        position = this.beat2positionAdjusted(beat);
        clock.beats = beat;
        if(position >= 0){ 
            this.jumpToPosition(position);
        }{ // if the position is lower than zero, there is nothing in the buffer to play, so we pause the synth temporarily, and activate when we arrive at the start of the file
            synth.run(false);
            this.jumpToPosition(0);            
            clock.schedAbs(this.position2beatAdjusted(0), {synth.run(true)});
        }
    }

    jumpToPosition { |position|
        // we use busses to obtain synchronicity between lang and server; we write a random number to the jumpToPositionNumber because this is the way I saw to transmit discrete signal to the server (in combination with a Changed UGen)
        positionSetBus.setSynchronous(position);
        jumpToPositionBus.setSynchronous(1.0.rand);
        this.updateUserInducedGridOffsetStatic;
    }

    spawnSynths {
        synth = Synth.newPaused("DJdeck",[\bufnum, buffer, \deckTempoBus, clock.bus, \trackTempo, trackTempo, \referenceBufnum, referenceBuffer, \referenceBus, referenceBus, \positionSetBus, positionSetBus, \jumpToPositionBus, jumpToPositionBus]);
    }

    testReference {
        testBuffer = Buffer.loadCollection(collection: frameTrackerArray[0..track.numFrames]);
        testBus = Bus.control;
        synth.set(\outputBus, testBus, \bufnum, testBuffer);
    }

    testBeatsReference {
        ^this.position2beat(testBus.getSynchronous);
    }
}