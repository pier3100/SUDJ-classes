/*
DESIGN
- should be linked to a recorder and slicer
    - recorder should be able to make a quatized recording from any bus and save this to disk
    - slicer should be able to load a soundfile (a recording or a track) from disk, and load it to the sampler, such that each slot of the sampler is a different slice
- should be able to load a sample pack 
    - what is right format for a sample pack; -> lets for now put the sample on a grid in an audiofile, and give it a specific name i.e. samplePackAudio
    - smaple packs contain 4 samples
    - should be able to auto select associated rhytm
- rhythms, as described in a .midi file, should be loaded during initialization; name may correspond to sample pack
- the sampler has one active rhythm
    - the rhythm may be altered using a midiInterface
    - the rhythm is implemented as a pattern
- there is an associated SynthDef which is just a simple buffer player with:
    - with an envelop
    - some atandard post processing: panning, EQ, velocity
- the pattern
    - create with Pseq and Pbind from arrays
    - make methods which have the step as an argument (step corresponds to element in array)
    - we have an array called patternArray, 
        - length = number of samples
        - each element is an array called patternDefinition;
            - length = number of quantities we need to describe the pattern (i.e. velocity, length, pitch, reverb, etc)
            - each element is an array called for example velocity
                - length = number of steps = 16
                - each element is a number
    - lets define an object for the patternDefinition

TODO
- midi feedback needs to be able to be also pushed from the side of the target, use dependency system
- integrate into ecosystem
- midi mapping

NICE TO HAVE
- add feature which allows you to go to the next page. The next page is a copy of the first page, but linked to the second bar. You can now modify the second bar.
    - when to reset the second bar page
        -> upon selecting new base rhythm
        -> upon pressing reset
- make it stereo with a pseudoWidth control (width of zero means take the left input for both the left and right track; implement by forwarding the left input directly to the left output, while crossfading between left and right input for the right output)
    - we also need to have a corresponding stereoEncoder without kernelLatency
- allow for changing the looplength
- we need to be able to save patterns to midi

SKIPPED
- think about switching between buffers while playing, would offer creative flexibility; but how to time it? you have the buffer loading time and the seerver latency
    -> maybe load all buffers in advance? and just switch the buffer in the synthdef
    - each single pattern should use a specific buffer; maybe we should provide this when instantiating the singlePatternDefinitions
    -> switching between buffers while playing does not offer much musical value, and is not so easy to implement

DONE
- make trimmed slicing more robust
- when reading the midi data, we need to check if there are midi events in the 0th track, if so, start with this track, otherwise start with track 1
- when reading midi data we can make use of the midiTrackEvents(#) method
- make two seperate arrays
    - one for the buffers 
    - one for the patterns
    -> the user needs to make sure he lines up the buffers and the patterns; this is a transparent approach and offers flexibility
*/

Sampler {
    var <outputBus, <bufferArray, <slotArray, clock, <isActive = false;
    var <patterns, <>selectedPatternIndex = 0, <multiSamplePaths, <>selectedMultiSampleIndex = 0;
    var <buttonEngaged = false, <buttonSlot = 0, <buttonStep = 0, <parameterChanged = false, previousPlayState = false;

    *initClass {
        this.addSynthDef;
    }

    *new { |nSlots, steps, aClock, folderPath, outputBus = 0|
        ^super.new.init(nSlots, steps, aClock, folderPath, outputBus);
    }

    init { |nSlots, steps, aClock, folderPath, outputBus_|
        outputBus = outputBus_;
        clock = aClock ? TempoClock.new(2);
        bufferArray = BufferArray.new(nSlots);
        slotArray = Array.fill(nSlots, { SinglePatternPlayer.new(steps, clock) });
        patterns = List.new(8);
        multiSamplePaths = List.new(8);
        folderPath !? { this.loadAllPatternsAndMultiSamples(folderPath) };
    }

    // modify pattern
    element_ { |val, sampleSlot, step, key|
        // it sets the property indicated by key of sampleslot, at step step, to val
        slotArray.[sampleSlot].patternDefinition.perform(key.asSetter).[step] = val;
        isActive = true;
    }

    element { |sampleSlot, step, key|
        // it gets the property indicated by key of sampleslot, at step step
        ^slotArray.[sampleSlot].patternDefinition.perform(key).[step];
    }

    // interface
    play {
        slotArray.do({|slot| slot.play });
        isActive = true;
    }

    pause {
        slotArray.do({|slot| slot.pause });
        isActive = false;
    }

    nextPattern { |direction = true|
        var increment;
        if(direction){ increment = 1}{ increment = -1 };
        selectedPatternIndex = (selectedPatternIndex + increment).clip(0,patterns.size);
        this.selectPattern(patterns[selectedPatternIndex]);
    }

    nextMultiSample { |direction = true|
        var increment;
        if(isActive.not){//multi samples may only be changed when paused
            if(direction){ increment = 1}{ increment = -1 };
            selectedMultiSampleIndex = (selectedMultiSampleIndex + increment).clip(0,multiSamplePaths.size);
            this.loadAndSelectMultiSample(multiSamplePaths[selectedMultiSampleIndex]);
        }
    }

    buttonEngage { |slot, step|
        if(buttonEngaged.not){ // we do not allow simultanious button presses
            buttonSlot = slot;
            buttonStep = step;
            previousPlayState = this.element(buttonSlot, buttonStep, \play); // write down for proper close out on button release
            if(previousPlayState.not){ this.element(true, buttonSlot, buttonStep, \play) }; //if off, turn on
            buttonEngaged = true;
        }
    }

    buttonDisengage { |inhibitTurnOff = false|
        if(previousPlayState && parameterChanged.not && inhibitTurnOff.not){ // only consider turning off when it was previously on; we did not change a parameter; and we do not inhibit (to be used for long press)
            this.element(false, buttonSlot, buttonStep, \play); // turn off
        };
        parameterChanged = false;
        buttonEngaged = false;
    }

    velocity_ { |val|
        if(buttonEngaged){
            this.element_(val, buttonSlot, buttonStep, \velocity);
        };
        parameterChanged = true;
    }

    velocity {
        ^this.element(buttonSlot, buttonStep, \velocity);
    }

    // patterns
    reset {
        isActive = false;
        slotArray.do({ |item| item.reset }); 
    }

    selectPattern { |multiPattern|
        var length;
        // we allow for the situation when the number of slots does not equal the number of entries in the patternFromDictionary
        length = multiPattern.size.min(slotArray.size);
        for(0, length - 1){ |i|
            slotArray[i].loadPattern(multiPattern[i]); // we write the ith singPattern from the multipattern to the slot
        };
    }
    
    loadPattern { |path|
        // the midi file should contain a track per slot; we assume C3 is the base frequency
        var key, pathName;
        pathName = path.asPathName; //allow for both pathName and string input
        key = pathName.fileNameWithoutExtension.asSymbol;
        ^SimpleMIDIFile.read(pathName.fullPath).asMultiPatternArray(16, { |index| Pbind(\instrument, \samplePlayer, \bus, outputBus, \buffer, bufferArray.bufferArray[index]) });
    }

    loadAllPatternsAndMultiSamples { |folderPath|
        // we create an array of samples and and array of patterns such that the following is true. Let m be the amount of samples/loops; the first m patterns correspond with these loops/samples
        var i = 0, patternPath, patternAllreadyAdded;
        patternAllreadyAdded = List.new(1);
        folderPath.asPathName.filesDo({ |path| if(path.extension == "wav"){ 
            multiSamplePaths.add(path.fullPath); // we store the path
            patternPath = path.pathOnly++path.fileNameWithoutExtension++".mid";
            if(File.exists(patternPath)){ patterns.add(this.loadPattern(patternPath)); patternAllreadyAdded.add(patternPath) }{ patterns.add(Array.fill(4, { |i| SinglePatternDefinition.new(16, Pbind(\instrument, \samplePlayer, \bus, outputBus, \buffer, bufferArray.bufferArray[i])) })) }; // load if it exists, otherwise load a dummy     
        } });
        folderPath.asPathName.filesDo({ |path| if((path.extension == "mid") && patternAllreadyAdded.includesEqual(path.fullPath).not){ 
            // now add the remaining patterns
            patterns.add(this.loadPattern(path));
        } });
        //select the currently indexed pattern and multisample
        this.selectPattern(patterns[selectedPatternIndex]);
        this.loadAndSelectMultiSample(multiSamplePaths[selectedMultiSampleIndex]);
    }

    loadAndSelectMultiSample { |audioPath|
        // you should supply the path of the audio file, we will lookup the associated pattern ourselves, this one is allready loaded
        var key;
        if(isActive.not){ // we protect ourselves and forbid to load new buffers, while buffers are playing
            bufferArray.loadSliced(audioPath.asPathName.fullPath);
            //key = audioPath.asPathName.fileNameWithoutExtension.asSymbol; // we assume that there is a pattern with the same name
            //this.loadMultiPattern(patternDictionary.at(key)); //TODO make robust against the pattern not existing
        }  
    }

    // backend
    *addSynthDef { // some mistakes in here
        // SynthDef classes
        Class.initClassTree(SynthDef);
        Class.initClassTree(SynthDescLib);
        
        // used UGens
        Class.initClassTree(Env);
        Class.initClassTree(EnvGen);
        Class.initClassTree(PlayBuf);
        Class.initClassTree(Out);

        // definition
        SynthDef(\samplePlayer, { |bus, buffer, velocity = 1, rate = 1, envelop|
            // we need to make some design choices, do we include a panner here (a panner per sample); or de want a panner per slot, or do we only need a panner for the entire sampler
            var env, envel,output;
            env = Env.new;
            output = PlayBuf.ar(1, buffer, rate) * velocity;
            envel = EnvGen.ar(env, doneAction: Done.freeSelf);
            output = output;
            Out.ar(bus, output);
        }).writeDefFile; // write it to standard location, server will load it on boat
    }
}

SinglePatternDefinition {
    var <pbind, <>play, <>velocity, <>pitch, <>envelop;

    *new { |steps, protoPbind|
        // steps per bar
        ^super.new.init(steps, protoPbind);
    }

    init { |steps, protoPbind_|
        var protoPbind;
        protoPbind = protoPbind_ ? Pbind(\instrument, \default);
        play = Array.fFalse(steps);
        velocity = Array.zeros(steps);
        pitch = Array.zeros(steps);
        envelop = Array.zeros(steps); //placeholder
        //might want to use PatternConductor here
        pbind = Pbind(\velocity, Pseq(play, inf), \pitch, Pseq(pitch, inf), \envelop, Pseq(envelop, inf), \delta, 4/steps, *protoPbind.patternpairs);
    }

    reset {
        play.setToZero;
        velocity.setToZero;
        pitch.setToZero;
        envelop.setToZero;
    }
}

SinglePatternPlayer {
    var activePatternDefinition, eventStreamProxy, myClock;

    *new { |steps, clock|
        ^super.new.init(steps, clock);
    }

    init { |steps, clock|
        eventStreamProxy = EventPatternProxy.new;
        myClock = clock;
        activePatternDefinition = SinglePatternDefinition.new(steps);
    }

    play {
        eventStreamProxy.play(myClock, quant: 4); //resume quantized by bar
    }

    pause {
        eventStreamProxy.stop; // we stopplayback entirely, because we restart the pattern from the beginning
    }

    loadPattern { |aPatternDefinition|
        activePatternDefinition = aPatternDefinition;
        eventStreamProxy.source = activePatternDefinition.pbind;
    }

    reset {
        activePatternDefinition.reset;
    }
}

BufferArray {
    var <bufferArray;
    // an array of buffers
    *new { |size = 4|
        ^super.new.init(size);
    }

    init { |size|
        bufferArray = Array.fill(size, { Buffer.alloc(Server.default, 100) });
    }

    loadSliced { |path, slices| 
        var nSlices, length, entireAudio;
        nSlices = slices ? bufferArray.size;
        entireAudio = Buffer.readChannel(Server.default, path, channels: [1], action: { |buf|
            length = buf.numFrames;
            for(0, nSlices - 1){ |i|
                entireAudio.copyTrimmed(bufferArray[i], ((length / nSlices) * i).asInteger, (length / nSlices).asInteger);
                //bufferArray[i].cache;
                //bufferArray[i].allocReadChannel(path, ((length / nSlices) * i).asInteger, (length / nSlices).asInteger, [1], { |buffer| buffer.queryMsg }); //we slice it in nSlice equal pieces
        }
        });
        //entireAudio.free;
        
        
    }
}

+ SimpleMIDIFile {
    asMultiPatternArray { |steps = 16, protoPbindGenerator|
        // we quantize to a grid of steps per bar; the protoPbindGenerator is a function which is fead the track number and should generate a Pbind
        if(this.format == 1){
            var division, multiPatternArray, startFromTrack = 0;
            this.timeMode_('ticks'); // first make sure settings are as we like them
            division = this.division; // write down the devision
            if(this.midiTrackEvents(0).isEmpty){ startFromTrack = 1 }; // typically a format 1 file has no midi events in the first track; however for files exported from Bitwig this is the case
            multiPatternArray = Array.fill(this.tracks - startFromTrack, { |i| SinglePatternDefinition.new(steps, protoPbindGenerator.value(i)) });
            this.midiEvents.do({ |item| // each event is [ trackNumber, absTime, type, channel, val1, val2 ]
                var step;
                if(item.[2] == 'noteOn'){
                    step = (item.[1] / (division * 4) * steps).round.asInteger;
                    multiPatternArray.[item[0] - startFromTrack].play.[step] = true; // write pitch
                    multiPatternArray.[item[0] - startFromTrack].pitch.[step] = item.[4].midicps; // write pitch
                    multiPatternArray.[item[0] - startFromTrack].velocity.[step] = item.[5] / 128; // write velocity
                }
            }) 
            ^multiPatternArray;
        }{
            Error("Only midi files with format type 1 supported").throw;
        }
    }
}