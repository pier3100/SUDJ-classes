MidiHidSystem : Object {
    classvar <initializedMidiHid = false;
    classvar <dummyBus, <macroToggle = false;
    classvar <instanceList;
    classvar <>activeMacroTarget, <dummyTarget;
    classvar <plcMacroMapping;
    classvar <>globalSensitivity = 1, <>globalSensitivitySetpoint = 0.2, <activateGlobalSensitivity = false;
    classvar <>enabled = true;

    // add this level we only setup the required infrastructure for macro mapping

    *new { |macroMappingFreq = 10|
        initializedMidiHid.not.if({ this.prInit(macroMappingFreq) });
        ^super.new;
    }

    *prInit { |macroMappingFreq = 10|
        plcMacroMapping = PLC.new(macroMappingFreq, active: { enabled }); // for macromapping from server to language
        this.initDummyBus;
        instanceList = List.new(10);
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

    storeMapping { |path|
        instanceList.writeArchive(path);
    }

    storePreset { |path|
        instanceList.do({ |item| item.storePreset }); // we write the currentValue to the baseValue
        this.storeMapping(path); // we store everything as an archive
    }

    *loadMapping { |path|
        ^Object.readTextArchive(path);
        // finally we need to deal with macroMappings, which is hard because we cannot right away assume the busnumbers are kept the same
        //    a solution - which has probably more benefits - is to not write down the bus we use for macromapping, but we hand over the entire object, which we write down as master
    }

    *loadPreset { |path|
        // this will write all the base values of the targets
        var preset;
        preset = this.loadMapping(path); // this is an array similar to the instanceList, but we cannot assume anything about this array
        preset.do({ |item| 
            var correspondingInstance;
            correspondingInstance = instanceList.select({ |inst| inst == item }).[0]; // find the corresponding instance; which makes extensive use of custom definitions of "=="
            correspondingInstance !? { correspondingInstance.loadPreset(item)}; // load the preset values, only if we found a corresponding instance
        });
    }

    *resetPreset {
        // this will return all targets to their basevalue
        instanceList.do({ |item| item.resetPreset });
    }

    *resetInitial {
        // this will return all targets to their initial values
        instanceList.do({ |item| item.resetInitial });
    }

    *post {
        instanceList.do({ |item, k| "%: ".postf( k ); item.postInfo });
    }

    *remove { |index|
        // remove the mapping by index
        var instanceToRemove;
        instanceToRemove = instanceList.removeAt(index);
        instanceToRemove.remove;
        instanceToRemove = nil;
    }

    *findByTarget { |object_, key_|
        ^instanceList.detect({ |item| item.target.class.superclasses.includes(AbstractTarget).if{ ((item.target.object==object_)&&(item.target.key==key_)) }{ false } }); // first we check whether it is a defined target})
    }

    == { |aMidiHidSystemInstance|
		^this.compareObject(aMidiHidSystemInstance, #[\source, \target]);
	}

	hash {
		^this.instVarHash(#[\source, \target]);
	}

}

MidiHidSystemTemplate : MidiHidSystem {
    var <source; //defines what is the input we are mapping
    var <target; // called upon input
    var <targetOut; // called for feedback to controller
    var <>active = true; // this allows to temporarily disable a midimapping based on some logic, should be a boolean or a function which return a boolean

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
        //add a new instance to the system, should call this.onInput when a new controller message arrives
    }

    onInput {
        //to be implemented in subclass
        //is clled upon a new controller message
        //should call this.value(), probably by by first calling massageMapping
    }

    feedback {
        //to be implemented in subclass
        //provide feedback to controller
    }

    loadPreset { |anotherInstance|
        //you might want to overwrite this in your subclass
        //load relevant values from otherInstance and write them to this
        if(target.respondsTo(\baseValue)){ target.baseValue = anotherInstance.target.baseValue };
    }

    resetPreset {
        //you might want to overwrite this in your subclass
        // reset the target(s); typically resetting them to the preset value which is stored as the baseValue
        if(target.respondsTo(\resetBaseValue)){ target.resetBaseValue; targetOut !? { this.feedback } };
    }

    storePreset {
        //you might want to overwrite this in your subclass
        //store current value in memory
        if(target.respondsTo(\storeBaseValue)){ target.storeBaseValue };
    }

    resetInitial {
        //you might want to overwrite this in your subclass
        // reset the target(s); typically resetting them to the initial value (of moment of creating the mapping)
        if(target.respondsTo(\resetInitialValue)){ target.resetInitialValue; targetOut !? { this.feedback } };
    }

    *resetLibraries {
        //to be implemented in subclass
    }    

    postInfo {

    }

    remove {

    }

/*     isSameMapping { |anotherInstance|
        var targetIsSame;
        if(anotherInstance.target == this.target){
            targetIsSame = true;
        }{
            if(anotherInstance.target.respondsTo(\object) && this.target.respondsTo(\object)){
                targetIsSame = (anotherInstance.target.object == )
            }
        }
        ^((anotherInstance.source == this.source) && ((anotherInstance.target == this.target) || (anotherInstance.target.objec)))
    } */
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

    add {
        instanceList.add(this);
    }

    remove {
        plcMidiFeedback.remove({ this.feedback });
    }

    postInfo {
        "\tSource: \t[%, \t%, \t%]\n".postf( source.midiCC, source.midiChannel, source.midiDevice.nameInput );
        "\tTarget: \t%\n".postf( target.asCompileString );
		"\tTargetOut: \t%\n".postf( targetOut.asCompileString );
    }
}

MidiCC : MidiSystem {
    classvar <initializedMidiCC = false;
    var <mode, <feedbackMode = true, <sensitivity, <acceleration, constrained, time;

    *new { |source, target, mode = \relative, feedbackMode = true, sensitivity = 1, acceleration = 0, constrained = true, active = true, targetOut|
        initializedMidiCC.not.if({ this.init });
        if(([\absolute, \relative, \relativeRound, \forwardBackwardButton, \plcFeedbackOnly].includes(mode)).not){ Error("% is not a valid mode.".format(mode)).throw }; // throw an error when the mode is not valid
        if(mode == \relative && target.respondsTo(\parameterValue).not){ Error("For the supplied target the method parameterValue has no implementation, hence we cannot handle relative mode").throw }; // if currentParameterValue is not implemented there is no way to handle a relative controller
        ^super.new.init(source, target, mode, feedbackMode, sensitivity, acceleration, constrained, active, targetOut);
    }

    *init {        
        MIDIdef.cc(\CC, { |val, cc, chan, srcID| // this the workhorse, here we setup what to do with new midi messages
            instanceList.do({arg item, i; // the item of Array a are a tuple, where the zeroth element is an MS (source) and the first element is a target on which we can call .value
                if(item.class == MidiCC){ // we check wether the item is indeed a cc instance
                    if(item.source.midiChannel == chan && item.source.midiCC == cc && item.source.midiDevice.indexIn == srcID){ // check wether instance responds to this message
                        item.onInput(val); // call the target
                    }
                }
            });
        }).permanent = true; //to make sure it survives cmd+.
        initializedMidiCC = true;
    }

    init { |source_, target_, mode_, feedbackMode_, sensitivity_, acceleration_, constrained_, active_, targetOut_|
        source = source_.asMidiSource;
        target = target_;
        mode = mode_;
        feedbackMode = feedbackMode_;
        sensitivity = sensitivity_; //so an input value of 1, is normal sensitivity
        acceleration = acceleration_;
        constrained = constrained_;
        time = Time.new;
        active = active_;
        source.midiDevice.midiOut !? { if(targetOut_.isNil){ if(target.respondsTo(\outputValue)){ targetOut = { target.outputValue }} }{ targetOut = targetOut_ } };// assign targetOut only if we are able to send it// standard assign targetOut_, if Nil, assign target.outputValue if available
        this.add;
        if(mode == \plcFeedbackOnly){ plcMidiFeedback.add({ this.feedback }) }; 
        targetOut !? { this.feedback }; // make sure the midi output is initially synced to its target
    }

    onInput { |val|
        // this will be called upon an incoming message 
        if(active.value(this)){ // only execute if active
            target.value(this.messageMapping(val));
            targetOut !? { this.feedback }; // we directly give feedback
        }
    }

    messageMapping { |val|
        var currentValue, value, increment, modifiedSensitivity;
        modifiedSensitivity = sensitivity * MidiHidSystem.globalSensitivity;
        if(mode==\absolute){
            value = val.linlin(0,127,0,1);//map the midi value to a value between 0,1
        }{
            if(mode==\forwardBackwardButton){ // to be used for encoders mapping to pitchbending
                if(val>60){ increment = val - 128}{ increment = val };//definition of relative behavior; XoneDX encoder go from 1 to 5 for clockwise turning slow to fast; and fo from 127 to 122 for anti clockwise slow to fast
                value = modifiedSensitivity * increment * (1 + this.accelerate(time.timeDifference,modifiedSensitivity));
            }{  
                if(mode==\relativeRound){
                    currentValue = target.parameterValue;
                    if(val>60){ increment = -1}{ increment = 1 };//definition of relative behavior
                    value = (currentValue + increment).asInteger;
                }{
                    //we can assume mode is relative, because we checked it earlier on
                    currentValue = target.parameterValue;
                    if(val>60){ increment = (val - 128) / 128}{ increment = val / 128 };//definition of relative behavior
                    value = currentValue + (modifiedSensitivity * increment);
                    //if(constrained){ value = value.clip(0,1) }; // this was previously commented away, not sure why, returned to operation on 27-11-24 EDIT (03-12) it was commented away because the clipping is done at the target
                }
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
        if(feedbackMode){
            message = this.prepareFeedbackMessage;
            if(active.value(this)){ source.midiDevice.midiOut.control(*message) }; // only send the feedback when the mapping is active
        }
    }

    prepareFeedbackMessage {
        ^[source.midiChannel, source.midiCC, targetOut.value.asFloat.linlin(0,1,0,127.99).floor]; // we use asFloat to account for targetOut returning a boolean
    }

}

MidiButton : MidiSystem {
    // the targetOff is supplied a second argument which is whether or not a delayedEventOccured (you can use this to make longButtonPress behavior)
    // push will call targetOn with value 1 on engage, and will call it again with 0 on this engage
    // toggle calls targetOn on engage with the inverse of the current value; it calls targetOff on disengage, also toggled
    // direct calls targetOn with value 1, and targetOff with value 0

    classvar <initializedMidiButton = false;
    var <targetOn, <targetOff, <mode, <feedbackMode = true, dynamicTask, delay, delayedEventOccured = false, <buttonValue;

    *new { |source, targetOn, targetOff, mode = \push, feedbackMode = true, delay = 0.0, active = true, targetOut, buttonValue = 1|
        if(([\push, \toggle, \direct, \feedbackOnly, \plcFeedbackOnly].includes(mode)).not){ Error("% is not a valid mode.".format(mode)).throw }; // throw an error when the mode is not valid
        if(mode == \toggle && targetOn.respondsTo(\parameterValue).not){ Error("Toggle mode requires the target to respond to .parameterValue").throw };
        initializedMidiButton.not.if({ this.init });
        ^super.new.init(source, targetOn, targetOff, mode, feedbackMode, delay, active, targetOut, buttonValue).add;
    }

    *init {
        MIDIdef.noteOn(\on, { |val, cc, chan, srcID|
            instanceList.do({arg item, i; // the item of Array a are a tuple, where the zeroth element is an MS (source) and the first elemenet is a SP (SynthParameter)
                if(item.class == MidiButton){
                    if(item.source.midiChannel == chan && item.source.midiCC == cc && item.source.midiDevice.indexIn == srcID){ 
                        item.noteOnAction(item.buttonValue.value); // instead of passing the cc value or anything like that, we evaluate the buttonValue, which typically is just 1
                    } 
                }

            });
        }).permanent = true;
        MIDIdef.noteOff(\off, { |val_, cc, chan, srcID|
            instanceList.do({arg item, i; // the item of Array a are a tuple, where the zeroth element is an MS (source) and the first elemenet is a SP (SynthParameter)
                if(item.class == MidiButton ){
                    if(item.targetOff.isNil.not){ // we only need to enact an noteOff action if one is defined
                        if(item.source.midiChannel == chan && item.source.midiCC == cc && item.source.midiDevice.indexIn == srcID){ 
                            item.noteOffAction;
                        }                         
                    }
                }
            });
        }).permanent = true;
        initializedMidiButton = true;
    }

    init { |source_, targetOn_, targetOff_, mode_, feedbackMode_, delay_, active_, targetOut_, buttonValue_|
        source = source_.asMidiSource;
        targetOn = targetOn_;
        target = targetOn; // for compability
        mode = mode_;
        feedbackMode = feedbackMode_;
        buttonValue = buttonValue_;
        if(mode == \push){ targetOff = targetOn }{
            targetOff = targetOff_; //normally 
        };
        delay = delay_;
        if(delay > 0){dynamicTask = DynamicTask.new(SystemClock, { this.noteOnBasicAction; delayedEventOccured = true })}; // in case we need to let event only occur if pressed more than delay time, we schedule it; and if needed cancel it
        active = active_;
        source.midiDevice.midiOut !? { if(targetOut_.isNil){ if(targetOn.respondsTo(\outputValue)){ targetOut = { targetOn.outputValue }; targetOn.addDependant(this) } }{ targetOut = targetOut_; targetOut.addDependant(this) } };// assign targetOut only if we are able to send it// standard assign targetOut_, if Nil, assign target.outputValue if available
        targetOut !? { this.feedback }; // make sure the midi output is initially synced to its target
        if(mode == \plcFeedbackOnly){ plcMidiFeedback.add({ this.feedback }) }; 
    }

    messageMapping { |val|
        var currentValue, value, increment;
        if(mode==\toggle){ value = targetOn.parameterValue.not };
        if(mode==\push){ value = val }; // note that in init we have set the MIDIdef in which we write over the incoming velocity, such that for noteOn it is always 1, and for noteOff always 0
        if(mode==\direct){ value = val };
        ^value;
    }

    noteOnAction { |val|
        if(active.value(this)){
            if(delay > 0){ // to implement that a certain amount of time needs to be pushed before the action takes place, we schedule the task and cancel it if we release it earlier
                dynamicTask.sched(delay); // sched the normal behavior (we have determined that dynamicTask = normal behavior earlier)
            }{ 
                this.noteOnBasicAction(val);
            }
        }
    }

    noteOnBasicAction { |val|
        targetOn.value(this.messageMapping(val)); // normal behavior; we hardcode that noteOn means value == 1
        targetOut !? { this.feedback };
    }

    noteOffAction {
        if(delay > 0){ dynamicTask.cancel }; // at this point either the dynamicTask has already been executed (cancelling it doesn't matter), or we release the button earlier than the delay time, and we want to cancel the scheduled event (because should only be executed if button pushed > delay)
        if(active){
            targetOff.value(this.messageMapping(0), delayedEventOccured); // anyhow execute the targetOff, we always evaluate the noteOffAction with value 0; we supply the delayedEventOccured; you can use this in your targetOff if you like
            targetOut !? { this.feedback };
        };
        delayedEventOccured = false; // we reset this to the default value
    }

    feedback {
        var message, value;
        value = targetOut.value.asFloat;
        //if(active.value(this)){// only send the feedback when the mapping is active, zie Onenote, supercollider, macrostructuur 2024-12-02
        if(feedbackMode){
            if(mode == \feedbackOnly){
                if(value >= buttonValue){ 
                    message = [source.midiChannel, source.midiCC, source.midiDevice.lightingSkin[1]];
                    source.midiDevice.midiOut.noteOn(*message);
                }{ 
                    message = [source.midiChannel, source.midiCC, source.midiDevice.lightingSkin[0]];
                    source.midiDevice.midiOut.noteOn(*message);
                };
            }{
                if(value == buttonValue){ 
                    message = [source.midiChannel, source.midiCC, source.midiDevice.lightingSkin[1]];
                    source.midiDevice.midiOut.noteOn(*message);
                }{ 
                    message = [source.midiChannel, source.midiCC, source.midiDevice.lightingSkin[0]];
                    source.midiDevice.midiOut.noteOn(*message);
                };
            }
        }
        //};
    }

    loadPreset { |anotherInstance|
        if(targetOn.respondsTo(\baseValue)){ targetOn.baseValue = anotherInstance.targetOn.baseValue };
        if(targetOff.responds(\baseValue) && anotherInstance.targetOff.responds(\baseValue)){ targetOff.baseValue = anotherInstance.targetOff.baseValue }; //TODO should be neater if we make sure targetOff is the same, but also we should verify more early that these are indeed Buttons
    }

    update { |theChanged, theChanger|
        targetOut !? { this.feedback };
    }
}

HidSystem : MidiHidSystemTemplate {
    classvar <initializedHid = false;
    
    *new {
        initializedHid.not.if({ this.initHid });
        ^super.new;
    }

    *initHid {
        HID.findAvailable;
        HID.action_({ |val, valRaw, elementUsage, elementUsagePage, elementId, element deviceId, device|
            instanceList.do({ |item, i|
                if(item.class.superclass == HidSystem){
                    if(item.source.elementId == elementId && item.source.hidDevice == device){
                        item.onInput(val, valRaw);
                    };
                };
            });
        });
        initializedHid = true;
    }

    *resetLibraries {
    }

    add {
        instanceList.add(this);
    }

    remove {
    }

    postInfo {
        "\tSource: \t[%, \t%]\n".postf( source.elementId, source.hidDevice.info.productName );
        "\tTarget: \t%\n".postf( target.asCompileString );
		"\tTargetOut: \t%\n".postf( targetOut.asCompileString );
    }
}

HidCC : HidSystem {
    var mode;
    *new { |source, target, mode = \absolute, active = true|
        ^super.new.init(source, target, mode, active).add;
    }

    init { |source_, target_, mode_, active_|
        source = source_.asHidSource;
        target = target_;
        mode = mode_;
        active = active_;
    }

    onInput { |val|
        // this will be called upon an incoming message 
        if(active.value(this)){ // only execute if active
            target.value(val);
        }
    }
}

HidValRaw : HidSystem {
    // this deviates from the standard, this target does not accept a 0, 1 value, but instead an integer
    *new { |source, targetFunction, active = true|
        ^super.new.init(source, targetFunction, active).add;
    }

    init { |source_, target_, active_|
        source = source_.asHidSource;
        target = target_;
        active = active_;
    }

    onInput { |val, valRaw|
        // this will be called upon an incoming message 
        if(active.value(this)){ // only execute if active
            target.value(valRaw);
        }
    }
}

MidiInOutPair : Object {
	var <indexIn, <indexOut, <midiOut, <nameInput, <nameOutput, <>lightingSkin;

	*new { |nameIn, nameOut, sysexInit, lightingSkin| //standard way of initiating is by providing a part of the name
		var tempIndexIn, tempIndexOut;
        MIDIClient.initialized.not.if({MIDIClient.init()});
        (tempIndexIn = MIDIClient.sources.detectIndex { |endpoint| endpoint.name.contains(nameIn)}) ?? { "WARNING: indexIn = Nil for %, a dummy will be used".format(nameIn).log(this) };
        nameOut !?({
            (tempIndexOut = MIDIClient.destinations.detectIndex { |endpoint| endpoint.name.contains(nameOut)}) ?? { "WARNING: indexOut = Nil for %".format(nameOut).log(this) };
        });
		^super.new.init(tempIndexIn,tempIndexOut, sysexInit, lightingSkin);
	}

	*byIndex { |indexIn_, indexOut_, sysexInit, lightingSkin|
		MIDIClient.initialized.not.if({MIDIClient.init()});
		^super.new.init(indexIn_,indexOut_, sysexInit, lightingSkin);
	}

	init { |indexIn_, indexOut_, sysexInit, lightingSkin_|
		indexIn = indexIn_;
		indexOut = indexOut_;
        nameInput = if(indexIn.isNil){ "dummy" }{ MIDIClient.sources[indexIn].name }; //support for dummy's if input is not found
		indexOut !? {
            nameOutput = MIDIClient.destinations[indexOut].name;
            midiOut = MIDIOut(indexOut);
            sysexInit !? { midiOut.sysex(Int8Array.newFrom(sysexInit)) }
        };
        lightingSkin = lightingSkin_ ? [0, 127]; //standard feedback
	}

	isMidiInOutPair { 
		^true
	}

    == { |aMidiInOutPair|
		^this.compareObject(aMidiInOutPair, #[\nameinput, \nameOutput]);
        // this becomes problematic if we have devices with the same name, but in this case we have bigger problems, as there is not a decent way to distinuigh devices with the same name
	}

	hash {
		^this.instVarHash(#[\nameinput, \nameOutput]);
	}

    customLighting { |lightingSkin_|
        ^this.copy.lightingSkin_(lightingSkin_);
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

    asMidiSource {
        ^this;
    }

    == { |aMidiSource|
		^this.compareObject(aMidiSource, #[\midiDevice, \midiChannel, \midiCC]);
	}

	hash {
		^this.instVarHash(#[\midiDevice, \midiChannel, \midiCC]);
	}
}

HidSource {
    var <elementId, <hidDevice;
    *new { |elementId, hidDevice|
        ^super.new.init(elementId, hidDevice);
    }

    init { |elementId_, hidDevice_|
        elementId = elementId_;
        hidDevice = hidDevice_;
    }

    asHidSource {
        ^this;
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
        ^MidiSource.new(this[2], this[1], this[0]);
    }

    asHidSource {
        ^HidSource.new(*this);
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


