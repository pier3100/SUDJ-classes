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
- think about switching between buffers while playing, would offer creative flexibility; but how to time it? you have the buffer loading time and the seerver latency
    -> maybe load all buffers in advance? and just switch the buffer in the synthdef
- each single pattern should use a specific buffer; maybe we should provide this when instantiating the singlePatternDefinitions

DONE

*/

Sampler {
    var bufferArray, slotArray, patternDictionary, clock, isActive = false;

    *initClass {
        this.addSynthDef;
    }

    *new { |nSlots, steps, aClock|
        ^super.new.init(nSlots, steps, aClock);
    }

    init { |nSlots, steps, aClock, folderPath|
        var tempo;
        tempo = aClock ? 2;
        clock = MyTempoClock.new(tempo);
        bufferArray = BufferArray.new(nSlots);
        slotArray = Array.fill(nSlots, { SinglePatternPlayer.new(steps, clock) });
        patternDictionary = Dictionary.new(8);
        folderPath !? this.loadAllPatternsFromMidi(folderPath);
    }

    samplePlayback_ { |bool, sampleSlot, step|
        // set whether the sample should play at step
        this.element_(bool, sampleSlot, step, \velocity);

        // redundant -> instead of using methods per property, we can directly use the element method
    }

    element_ { |val, sampleSlot, step, key|
        // it sets the property indicated by key of sampleslot, at step step, to val
        slotArray.[sampleSlot].patternDefinition.perform(key.asSetter).[step] = val;
        isActive = true;
    }

    element { |sampleSlot, step, key|
        // it gets the property indicated by key of sampleslot, at step step
        ^slotArray.[sampleSlot].patternDefinition.perform(key).[step];
    }

    // patterns
    reset {
        isActive = false;
        slotArray.do({ |item| item.reset }); 
    }

    playMultiPattern { |aPatternKey|
        var multiPatternFromDictionary, length;
        // we allow for the situation when the number of slots does not equal the number of entries in the patternFromDictionary
        multiPatternFromDictionary = patternDictionary.at(aPatternKey);
        length = multiPatternFromDictionary.size.min(slotArray.size);
        for(0, length){ |i|
            slotArray.[i].playPattern(multiPatternFromDictionary.[i]); // we write the ith singPattern from the multipattern to the slot
        }
        isActive = true;
    }
    
    loadPatternFromMidi { |path|
        // the midi file should contain a track per slot; we assume C3 is the base frequency
        var key;
        key = path.asPathName.fileNameWithoutExtension.asSymbol;
        patternDictionary.put(key, SimpleMIDIFile.read(path).asMultiPatternArray);
    }

    loadAllPatternsFromMidi { |folderPath|
        folderPath.asPathName.filesDo({ |path| this.loadPatternFromMidi(path) });
    }

    loadLoop { |audioPath|
        // you should supply the path of the audio file, we will lookup the associated pattern ourselves, this one is allready loaded
        var key;
        if(isActive.not){ // we protect ourselves and forbid to load new buffers, while buffers are playing
            bufferArray.loadSliced(audioPath);
            key = audioPath.asPathName.fileNameWithoutExtension.asSymbol; // we assume that there is a pattern with the same name
            this.playMultiPattern(patternDictionary.at(key)); //TODO make robust against the pattern not existing
        }  
    }

    // backend
    *addSynthDef { // some mistakes in here
/*         SynthDef(\samplePlayer, { |bus, buffer, velocity, rate, envelop|
            // we need to make some design choices, do we include a panner here (a panner per sample); or de want a panner per slot, or do we only need a panner for the entire sampler
            var output;
            output = PlayBuf.ar(buffer, rate) * velocity;
            output = output * Envgen(envelop);
            Out.ar(bus, output);
        }).writeDefFile; // write it to standard location, server will load it on boat
    */
    }
}

SinglePatternDefinition {
    var <pbind, <>play, <>velocity, <>pitch, <>envelop;

    *new { |steps|
        ^super.new.init(steps);
    }

    init { |steps|
        play = Array.fFalse(steps);
        velocity = Array.zeros(steps);
        pitch = Array.zeros(steps);
        envelop = Array.zeros(steps); //placeholder
        //might want to use PatternConductor here
        pbind = Pbind(\instrument, \samplePlayer, \velocity, Pseq(play, inf), \pitch, Pseq(pitch, inf), \envelop, Pseq(envelop, inf), \delta, 1/steps);
    }

    reset {
        play.setToZero;
        velocity.setToZero;
        pitch.setToZero;
        envelop.setToZero;
    }
}

SinglePatternPlayer {
    var activePatternDefinition, eventStreamProxy;

    *new { |steps, clock|
        ^super.new.init(steps, clock);
    }

    init { |steps, clock|
        eventStreamProxy = EventPatternProxy.default;
        eventStreamProxy.play(clock);
        activePatternDefinition = SinglePatternDefinition.new(steps);
    }

    playPattern { |aPatternDefinition|
        activePatternDefinition = aPatternDefinition;
        eventStreamProxy.source = activePatternDefinition.pbind;
    }

    reset {
        activePatternDefinition.reset;
    }
}

BufferArray {
    var bufferArray;
    // an array of buffers
    *new { |size|
        bufferArray = Array.fill(size, { Buffer.alloc(Server.default, 100) });
    }

    loadSliced { |path, slices| 
        var nSlices;
        nSlices = slices ? bufferArray.size;
        length = SoundFile.new(path).numFrames;
        for(0, nSlices - 1){ |i|
            bufferArray.[i].read(path, ((numFrames / nSlices) * i).asInteger); //we slice it in nSlice equal pieces
        }
    }
}

+ SimpleMIDIFile {
    asMultiPatternArray { |steps|
        var multiPatternArray;
        // first make sure settings are as we like them
        this.timeMode_('ticks');
        this.division = 1024;
        multiPatternArray = Array.fill(this.tracks - 1, { SinglePatternDefinition.new(steps) });
        this.midiEvents.do({ |item| // each event is [ trackNumber, absTime, type, channel, val1, val2 ]
            var step;
            if(item.[2] = 'noteOn'){
                step = (item.[1]/256).round.asInteger;
                multiPatternArray.[item[0] - 1].pitch.[step] = item.[4].midicps; // write pitch
                multiPatternArray.[item[0] - 1].velocity.[step] = item.[5] / 128; // write velocity
            }
        }) 
    }
}