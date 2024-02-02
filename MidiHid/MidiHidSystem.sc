MidiHidSystem : Object {
    classvar <initializedMidiHid = false;
    classvar <dummyBus, <macroToggle = false;
    classvar <>activeMacroTarget, <dummyTarget;
    classvar <plcMacroMapping;
    classvar <>globalSensitivity = 1, <>globalSensitivitySetpoint = 0.2, <activateGlobalSensitivity = false;
    // add this level we only setup the required infrastructure for macro mapping

    *new { |macroMappingFreq = 10|
        initializedMidiHid.not.if({ this.prInit(macroMappingFreq) });
        ^super.new;
    }

    *prInit { |macroMappingFreq = 10|
        plcMacroMapping = PLC.new(macroMappingFreq); // for macromapping from server to language
        this.initDummyBus;
        
        initializedMidiHid = true;
        this.initDummyTarget;
        this.resetMacroTarget;
        this.resetLibraries;
    }

    *resetAllLibraries {        
        this.resetLibraries;
        this.allSubclasses.do({ |item| item.resetLibraries }); // reset all libraries of subclasses, for convenience
    }

    *resetLibraries {
        initializedMidiHid.if({ plcMacroMapping.reset });
    }

    *macroToggle_ { |boolean|
        macroToggle = boolean;
        macroToggle.not.if({ this.resetMacroTarget }); // when existing macro mode, make sure the macro bus nr equals again the dummy bus
    }

    *initDummyBus {
        dummyBus = Bus.control(numChannels: 2);
        dummyBus.setSynchronous(-1); // the first value of this bus is the crossfade, which should definitely be -1, i.e. take the dry signal
    }    

    *initDummyTarget {
        dummyTarget = MacroTarget.new(key: \notReference);
    }

    *resetMacroTarget {
        activeMacroTarget = dummyTarget;
    }

    *activateGlobalSensitivity_ { |bool|
        activateGlobalSensitivity = bool;
        if(activateGlobalSensitivity){ globalSensitivity = globalSensitivitySetpoint }{ globalSensitivity = 1 };
    }
}

MidiHidSystemTemplate : MidiHidSystem {
    var target;
    *init {
        //to be implemented in subclass
        //setup some system which process midi/hid/osc messages
    }

    init {
        //to be implemented in subclass
    }

    messageMapping { |val|
        //to be overwritten in subclass, it should translate the raw midi/osc/hid message to a value typically between 0 and 1; use in the following sense  { |val| target.value(this.messageMapping(val)) }
        ^val;
    }

    add {
        //to be implemented in subclass
        //add a new instance to the system, should call this.value()
    }

    *resetLibraries {
        //to be implemented in subclass
    }
}

MidiSystem : MidiHidSystemTemplate {
    classvar <initializedMidi = false;
    classvar <plcMidiFeedback;
    
    *new { |midiFeedbackFreq = 2|
        initializedMidi.not.if({ this.initMidi(midiFeedbackFreq) });
        ^super.new;
    }

    *initMidi { |midiFeedbackFreq = 2|
        MIDIClient.initialized.not.if{ MIDIClient.init };
        MIDIIn.connectAll;
        
        plcMidiFeedback = PLC.new(midiFeedbackFreq);
        
        initializedMidi = true;
    }

    *resetLibraries {
        initializedMidi.if({ plcMidiFeedback.reset});
    }
}

MidiCC : MidiSystem {
    classvar <initializedMidiCC = false;
    classvar <midiCCMappingDictionary;
    var <midiSource, <target, <mode, <sensitivity, <acceleration, constrained, time;

    *new { |midiSource, target, mode = \relative, sensitivity = 1, acceleration = 0, constrained = true|
        initializedMidiCC.not.if({ this.init });
        if(([\absolute, \relative, \forwardBackwardButton].includes(mode)).not){ Error("% is not a valid mode.".format(mode)).throw }; // throw an error when the mode is not valid
        if(mode == \relative && target.respondsTo(\parameterValue).not){ Error("For the supplied target the method parameterValue has no implementation, hence we cannot handle relative mode").throw }; // if currentParameterValue is not implemented there is no way to handle a relative controller
        ^super.new.init(midiSource, target, mode, sensitivity, constrained).add;
    }

    *init {        
        MIDIdef.cc(\CC, { |val, cc, chan, srcID| // this a the workhorse, here we setup what to do with new midi messages
            midiCCMappingDictionary.do({arg item, i; // the item of Array a are a tuple, where the zeroth element is an MS (MidiSource) and the first element is a target on which we can call .value
                if(item[0].midiChannel == chan && item[0].midiCC == cc && item[0].midiDevice.indexIn == srcID)
                { item[1].value(val) }
            });
        }).permanent = true; //to make sure it survives cmd+.
        initializedMidiCC = true;
        this.resetLibraries;
    }

    *resetLibraries {
        initializedMidiCC.if({ midiCCMappingDictionary = Array.new(400) });
    }

    init { |midiSource_, target_, mode_, sensitivity_, acceleration_, constrained_|
        midiSource = midiSource_.asMidiSource;
        target = target_;
        mode = mode_;
        sensitivity = sensitivity_; //so an input value of 1, is normal sensitivity
        acceleration = acceleration_;
        constrained = constrained_;
        time = Time.new;
    }

    add {
        midiCCMappingDictionary.add([midiSource, { |val| target.value(this.messageMapping(val)) }]); //we add our new midi mapping to the list which evaluated upon each midi message, and we apply our message mapping (.value will be called on this, which in turn calls the message mapping)
        if(target.respondsTo(\outputValue)&&midiSource.midiDevice.midiOut.isNil.not){ plcMidiFeedback.add({ this.feedback }) }; //for midi feedback
        
    }

    messageMapping { |val|
        var currentValue, value, increment, modifiedSensitivity;
        modifiedSensitivity = sensitivity * MidiHidSystem.globalSensitivity;
        if(mode==\absolute){
            value = val.linlin(0,127,0,1);//map the midi value to a value between 0,1
        }{
            if(mode==\forwardBackwardButton){ // to be used for encoders mapping to pitchbending
                var amount;
                amount =  modifiedSensitivity + this.accelerate(time.timeDifference,modifiedSensitivity);
                amount = 1.0;
                if(val==127){ value = 0.0 - amount }{ value = amount };
            }{  //we can assume mode is relative, because we checked it earlier on
                currentValue = target.parameterValue;
                if(val==127){ increment = -1/128}{ increment = 1/128 };//definition of relative behavior
                value = currentValue + (modifiedSensitivity * increment);
                if(constrained){ value = value.clip(0,1) };
        }};
        ^value;
    }

    accelerate { |timeDifference, modifiedSensitivity|
        // ramps down from acceleration*modifiedSensivity for timeDifference = 0 to 0 at timeDifference = accelearation and is 0 onward
        // such that if you fire repeated midi target is called for a higher value, when midi message are closer together, but it remains bounded
        var output;
        if(timeDifference < (acceleration)){ output = (acceleration * modifiedSensitivity) - timeDifference * modifiedSensitivity }{ output = 0 };
        ^output;
    }

    feedback {
        var message;
        message = this.prepareFeedbackMessage;
        midiSource.midiDevice.midiOut.control(*message);
    }

    prepareFeedbackMessage {
        ^[midiSource.midiChannel, midiSource.midiCC, target.outputValue.linlin(0,1,0,127.99).floor];
    }

}

MidiButton : MidiSystem {
    classvar <initializedMidiButton = false;
    classvar <midiNoteOnMappingDictionary, <midiNoteOffMappingDictionary;
    var <midiSource, <targetOn, <targetOff, mode;

    *new { |midiSource, targetOn, targetOff, mode = \push|
        initializedMidiButton.not.if({ this.init });
        ^super.new.init(midiSource, targetOn, targetOff, mode).add;
    }

    *init {
        MIDIdef.noteOn(\on, { |val, cc, chan, srcID|
            var velocity = 1; // overwrite the val value to 0 for noteOff button messages
            midiNoteOnMappingDictionary.do({arg item, i; // the item of Array a are a tuple, where the zeroth element is an MS (MidiSource) and the first elemenet is a SP (SynthParameter)
                if(item[0].midiChannel == chan && item[0].midiCC == cc && item[0].midiDevice.indexIn == srcID)
                { item[1].value(velocity) } // the item here will in general be something like {|val| target.messagemapping(val)}
            });
        }).permanent = true;
        MIDIdef.noteOff(\off, { |val_, cc, chan, srcID|
            var velocity = 0; // overwrite the val value to 0 for noteOff button messages
            midiNoteOffMappingDictionary.do({arg item, i; // the item of Array a are a tuple, where the zeroth element is an MS (MidiSource) and the first elemenet is a SP (SynthParameter)
                if(item[0].midiChannel == chan && item[0].midiCC == cc && item[0].midiDevice.indexIn == srcID)
                { item[1].value(velocity) } 
            });
        }).permanent = true;
        initializedMidiButton = true;
        this.resetLibraries;
    }

    init { |midiSource_, targetOn_, targetOff_, mode_|
        midiSource = midiSource_.asMidiSource;
        targetOn = targetOn_;
        mode = mode_;
        if(mode == \push){ targetOff = targetOn }{
            targetOff = targetOff_; //normally 
        };
    }

    messageMapping { |val|
        var currentValue, value, increment;
        if(mode==\toggle){ value = targetOn.parameterValue.not };
        if(mode==\push){ value = val };
        ^value;
    }

    add {
        midiNoteOnMappingDictionary.add([midiSource, { |val| targetOn.value(this.messageMapping(val)) }]);
        targetOff !?({ midiNoteOffMappingDictionary.add([midiSource, { |val| targetOff.value(this.messageMapping(val)) }]) });       
    }

    *resetLibraries {
        initializedMidiButton.if({
            midiNoteOnMappingDictionary = Array.new(400);
            midiNoteOffMappingDictionary = Array.new(400);
        });
    }

/*     feedback {
        var message;
        message = this.prepareFeedbackMessage;
        midiSource.midiDevice.midiOut.control(*message);
    }

    prepareFeedbackMessage {
        ^[midiSource.midiChannel, midiSource.midiCC, target.outputValue.linlin(0,1,0,127.99).floor];
    } */
}

MidiInOutPair : Object {
	var <indexIn, <indexOut, <midiOut;

	*new { |nameIn, nameOut| //standard way of initiating is by providing a part of the name
		var tempIndexIn, tempIndexOut;
        MIDIClient.initialized.not.if({MIDIClient.init()});
        tempIndexIn = MIDIClient.sources.detectIndex { |endpoint| endpoint.name.contains(nameIn)};
        nameOut!?({
            tempIndexOut = MIDIClient.destinations.detectIndex { |endpoint| endpoint.name.contains(nameOut)}
        });
		^super.new.init(tempIndexIn,tempIndexOut);
	}

	*byIndex { |indexIn_, indexOut_|
		MIDIClient.initialized.not.if({MIDIClient.init()});
		^super.new.init(indexIn_,indexOut_);
	}

	init { |indexIn_, indexOut_|
		indexIn = indexIn_;
		indexOut = indexOut_;
		indexOut!?({
            midiOut = MIDIOut(indexOut)
        });
	}

	isMidiInOutPair { 
		^true
	}
}

MidiSource : Object { //Midi Source
    var <midiDevice, <midiChannel, <midiCC; //midiCC is used to store the note number for note messages

    *new { |midiDevice, midiChannel, midiCC| //we assume the midiDevice is of class MidiInOutPair
        ^super.new.init( midiDevice, midiChannel, midiCC)
    }

    init { |midiDevice_, midiChannel_, midiCC_|
        midiDevice =  midiDevice_;
        midiChannel = midiChannel_;
        midiCC = midiCC_;
    }
}

MySynthDef : SynthDef {
    classvar <resetLibrary;

    *initClass {
		synthDefDir = Platform.userAppSupportDir ++ "/synthdefs/";
		// Ensure exists:
		synthDefDir.mkdir;

        Class.initClassTree(Dictionary);
        resetLibrary = Dictionary.new();
	}

    reset { |object| // typically a function
        resetLibrary.put(name,object);
    }
} 

Time {
    var previousTime = 0;

    *new {
        ^super.new;
    }

    timeDifference {
        var newTime, timeDifference;
        newTime = thisThread.seconds;
        timeDifference = newTime - previousTime;
        previousTime = newTime;
        ^timeDifference;
    }
}

+ Array {
    asMidiSource {
        ^MidiSource(this[0], this[1], this[2]);
    }
}

+ Synth { 
    setControlVal {arg key, val; //we can just as well use the set message because for index offset 0, this amounts to the same. That is seti(key,0,val) == set(key,val)
        this.seti(key, 0, val);
    }

    setControlBus {arg key, bus;
        this.seti(key, 1, bus);
    }
    
    reset {
        MySynthDef.resetLibrary.at(this.defName).value(this); // executed the specified reset object/function from the reset library
    } 

    installMacro {
        var bus;
        bus = Bus.control(numChannels:2);
        bus.setSynchronous(-1);
        ^this.set(\macroBus,bus);
    }
}

+ SimpleNumber {
    not {
        var output;
        if(this == 0){ output = 1 }{ output = 0};
        ^output;
    }
}


