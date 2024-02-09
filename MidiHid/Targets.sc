/* 
TODO
- no feedback needed when in absolute mode
- test preset system
    -> IMPORTANT: I realized that the preset has nothing to do with the controller/mapping; I should be completely independent; I has to do with the parameters of the synths and other devices (I realized that in Bitwig it stores the parameters of your synths irespective of your controller)
- test feedback for buttons
- toggling of que buttons does not work
- when mapping (in test_surround_rev2, at the bottom) knob to \pan, the parameterValue yields unexpected values, while the outputValue gives expected values
- add push encoder resetting functionality
- add quantization support (both for button and knobs, at all time frames)
- implement preset system
- midifeedback for buttons (start with buttons on XoneDX)
- advanced mappings: such as shift, and bitwigs 8 standard knobs; two approaches
    - make multiple mappings, and activate/deactivate mappings with custom logic (this is also the traktor seems to handle it)
    - make another class, a targetProxy; and then you can select the target based on something else; so lets say we can select a synth which we want to edit; then based on which synth we have, we also supply the corresponding target to the proxyTarget; so we will probably have a synthConsole, which contains methods to interact with it (to which we assign buttons); and contains a list of Players (where a player will be a wrapper for a synth, and includes a corresponding target object); if we change the synth we have selected, we also update the target function
    - maybe we don't need a "targetProxy to this", I think it suffices just to reassign the target in the MidiHid instance
        - we do get a problem though we our preset system, since this supposes correspondance between source and target
    - maybe better therefor to do it in the Traktor style, with duplicate mappings, and make some convenience container classes for this purpose

DONE
- check naming of classes, check whether syntax is nice
- make sure reference value is automatically updated on start of macro-mapping to initial value -> only when corresponding knob is turned
- test buttons for server side variables
- test macroMapping to AbstractLangTarget
- test deleting macroMapping, for Langcontrol
- add mapping range to AbstractLangControl
- make sure that the current output value is written to the parameter value, when disengaging an active macro map
- rename parameterValue_ and parameterValue, such that these names correspond
- make sure macro's automatically allocate their own buffers
x make a parent class for AbstractLangTarget and LangBoolTarget
- implement sensitive mode for relative encoders
- make sure reference value is automatically updated on start of macro-mapping to initial value -> only when corresponding knob is turned LANGTARGET
- implement in project/physicalInterfaces
- midi feedback for buttons
- send midi feedback directly when receiving midi input (next to sending on the PLC), 
    - maybe via dependants system
    - rather define a targetOut maybe in MidiHidSystemTemplate
        - if not specified by user, but target.outputValue exists, then targetOut = {target.outputValue};
        - but the user can also manually specify a targetOut;
        - targetOut is called by the midiOutPLC,
        - targetOut is called to generate a midiOut message for each event for the input object; so whenever possibly we are updating something on the language or the server, due to the corresponding input, we also write a midiOut message
    - make preset system
        - for make one mother instanceList to which we add the entire instance, this offers more flexibility

NOTE
- a mapping to a instance variable of an object can be achieved by supplying this instance object directly.

NodeNumTarget
NodeBoolTarget
LangNumTarget
LangBoolTarget */

// a Target can be anything on which you can call .value

AbstractTarget : Object {
    //for a target to participate in the macro mapping infrastructure, which is implemented in ServerControl/LangControl/MacroOutput and enabled by the MidiHidSystem
    classvar initialized = false, dummyBus;
    var <macroBus; //the bus it sends or reads from
    var <object, <initialValue, <>baseValue;

    *new {
        initialized.not.if({this.init});
        ^super.new;
    }

    *init {
        MidiHidSystem.initializedMidiHid.not.if({ MidiHidSystem.prInit }); 
        this.initDummyBus;
        initialized = true;
    }

    *initDummyBus {
        dummyBus = Bus.control(numChannels: 2);
        dummyBus.setSynchronous(-1); //the first value of this bus is the crossfade, which should definitely be -1, i.e. take the dry signal
    }

    setMacroBus { |busNr|
        // direct access to the macroBus is required to output the current value of a servercontrol target, see below
        macroBus = Bus.new(rate: 'control', index: busNr, numChannels: 2);
    }

    value { |val| //so val is typically between 0 and 1
        //this is called in normal operation upon a new controller message
        this.parameterValue_(val);

        if(MidiHidSystem.macroToggle){ //here we implement the macroMapping behavior
            this.macroMappingBehavior;
        }
    }

    parameterValue_ {
        //to be implemented in subclass
        //should set the value of the object
    }

    macroMappingBehavior {
        //to be implemented in subclass
    }

    outputValue {
        //to be implemented in subclass
        //this is called for (midi) feedback
    }    

    parameterValue {
        //to be implemented in subclass
        //this is called for midicontrollers in relative mode       
    }

    resetBaseValue {
        //you might want to change this in your subclass
        //this is used to restore the parametervalue to the baseValue
        this.parameterValue_(baseValue);
    }

    resetInitialValue {
        //you might want to change this in your subclass
        //this is used to restore the parametervalue to the baseValue
        this.parameterValue_(initialValue);
    }

    storeBaseValue {
        //you might want to change this in your subclass
        //this is used to store the parametervalue to the baseValue
        baseValue = this.parameterValue;
    }

    == { |anAbstractTarget|
        // we say they are equal if the objects are of the same Class
        if(anAbstractTarget.respondsTo(\object)){
            ^(this.object.class == anAbstractTarget.object.class);
        }{
            ^false;
        }
	}

	hash {
        ^(this.class.hash << 1 bitXor: object.class.hash);
	}
}

AbstractServerTarget : AbstractTarget {
    var <key, <parameterBus, <constrained; //sensitivity is used in relative mode

    *new { |object, key|
        ^super.new.prInit(object, key);
    }

    prInit { |object_, key_|
        object = object_;
        key = key_;

        this.setupParameterBus;
    }

    installMacroBus { // actually assign the bus to read from, which contains the modulator
        object.setControlBus(key, macroBus.index);
    }

    setupParameterBus {
        //instead of setting the parameterVal directly, we create a control bus, to which we push a new value each time; this bus is mapped to the parameterVal control input, as indicated by \key; we do this because we have synchronius access to the bus values, which we do not have for the control inputs themselves
        parameterBus = Bus.control();
        object.get(key,{ |val|
            initialValue = val; //we write down the initial value, because this information is lost on server side after mapping the bus
            parameterBus.setSynchronous(initialValue);// make sure the bus has the same initial value as ServerControl, should be adapted for LangControl
            object.map(key,parameterBus);
        })
    }

    parameterValue {
        ^parameterBus.getSynchronous;
    }

    parameterValue_ { |val|
        parameterBus.setSynchronous(val);
    }

    == { |anAbstractServerTarget|
        // we say they are equal of the node the synth they refer to has the same name (so here I assume object is a synth, while I also want to allow for nodes TODO)
        if(anAbstractServerTarget.respondsTo(\object)){ 
            if(anAbstractServerTarget.object.respondsTo(\defName)){
                ^(this.object.defName == anAbstractServerTarget.object.defName);
            }
            ^false;
        }{
            ^false;
        }
	}

	hash {
        ^(this.class.hash << 1 bitXor: object.defName.hash);
	}
}

ServerTarget : AbstractServerTarget {
    *new { |object, key|
        ^super.new(object, key).init;
    }

    init {
        this.setMacroBus(dummyBus.index);
    }    
    
    outputValue {
        var parameterVal, sideChainCrossfade, sideChainInput;  
        // we have no access to the crossfaded output itself, so we need to recalculate it from the inputs
        parameterVal = this.parameterValue;
        # sideChainCrossfade, sideChainInput = macroBus.getnSynchronous(2);
        ^((0.5-(sideChainCrossfade/2))*parameterVal)+((0.5+(sideChainCrossfade/2))*sideChainInput);
    }

    macroMappingBehavior {
        // we overwrite the parameterValue with the current outputValue s.th. there are no jumps
        this.parameterValue_(this.outputValue);      

        this.setMacroBus(MidiHidSystem.activeMacroTarget.macroBus.index);
        // if possible we set the macro values to match the current values of the node/synth
        if((MidiHidSystem.activeMacroTarget.key==\ref)||(MidiHidSystem.activeMacroTarget.key==\reference)){ MidiHidSystem.activeMacroTarget.parameterValue_(this.parameterValue) };
        if((MidiHidSystem.activeMacroTarget.key==\cross)||(MidiHidSystem.activeMacroTarget.key==\crossfade)){ MidiHidSystem.activeMacroTarget.parameterValue_(0) };
        this.installMacroBus;
    }
}

MacroTarget : AbstractServerTarget {
    *new { |object, key|
        ^super.new(object, key).init;
    }
    
    init {
        object!?({ object.get(\macroBus, { |val| this.setMacroBus(val.asInteger) }) }); //write down the bus number of the macrosynth, such that we have easy access to this later on, for a MacroTarget the MacroBusNr is static and is the bus to which is written
        object??({ this.setMacroBus(dummyBus.index) }); // allow for a dummy target which does not refer to a synth/node, but has a valid macroBus
    }

    outputValue {
        ^this.parameterValue; // for a macro the current output value is just the parameter value
    }

    macroMappingBehavior {
        object.reset; // we reset the macro synth, the resetting behavior can be defined with the MySynthDef.reset(some function). This class/method is added in MidiHidSystem.sc.
        MidiHidSystem.activeMacroTarget = this; // write down the macroBusNr of this macro, as the global MidiHidSystem macro, which will be used in the current mapping process
    }
}

// a LangControl can be passed directly as a target, to be used if you want to pull the value upon request

ContinuousLangTarget : AbstractTarget {
    // ++ should be tested, and perhaps splitted in LangTarget (for object with some instance variable) and NumTarget (for numbers)
    // the data is pushed by a plc which is run in MidiHidSystem
    // this class should be used if you want to push to an object or number; if a macro is active, a plc is run to push the server data at a specified rate to the language
    classvar <>pushFreq;
    var <methodKey, <parameterValue, <outputValue, sideChainCrossfade, sideChainInput, range;

    *new { |object, methodKey, range|
        ^super.new.init(object,methodKey, range);
    }

    init { |object_, methodKey_, range_|
        object = object_;
        methodKey = methodKey_;
        range = range_??[0,1];
        initialValue = object.perform(methodKey);
        parameterValue = initialValue;
        this.setMacroBus(dummyBus.index);
        this.updateOutputValue;
    }

    macroMappingBehavior {
        // we overwrite the parameterValue with the current outputValue s.th. there are no jumps
        this.parameterValue_(this.outputValue);      

        // remove the current mapping
        try{ MidiHidSystem.plcMacroMapping.remove({ this.pushMacroMap }) };

        // if possible we set the macro values to match the current values of the node/synth
        if((MidiHidSystem.activeMacroTarget.key==\ref)||(MidiHidSystem.activeMacroTarget.key==\reference)){ MidiHidSystem.activeMacroTarget.parameterValue_(this.parameterValue) };
        if((MidiHidSystem.activeMacroTarget.key==\cross)||(MidiHidSystem.activeMacroTarget.key==\crossfade)){ MidiHidSystem.activeMacroTarget.parameterValue_(0) };

        // install the new mapping
        this.setMacroBus(MidiHidSystem.activeMacroTarget.macroBus.index);// for a AbstractLangPushTarget the macroBusNr is dynamic and refers to the bus from which the signal should be read
        if(macroBus.index!=MidiHidSystem.dummyBus.index){ //only install the new mapping if it not the the trivial place holder mapping
            MidiHidSystem.plcMacroMapping.add({ this.pushMacroMap });
        };

        
    }

    pushMacroMap {
        this.updateOutputValue;
        this.writeOutputValue;
    }

    updateOutputValue {
        # sideChainCrossfade, sideChainInput = macroBus.getnSynchronous(2);
        ^outputValue = ((0.5-(sideChainCrossfade/2))*parameterValue)+((0.5+(sideChainCrossfade/2))*sideChainInput);
    }

    writeOutputValue {
        object.perform(methodKey.asSetter,outputValue.linlin(0,1,range[0],range[1])); // we allow mapping to an alternative range, we do it here such that we can keep the idea intact of having values between 0,1 in our code
    }

    parameterValue_ { |val|
        parameterValue = val;
        this.updateOutputValue;
        this.writeOutputValue;
    }
} 

BoolLangTarget : AbstractTarget {
    var <methodKey, <parameterValue, <>outputValue;

    *new { |object, methodKey|
        ^super.new.init(object,methodKey);
    }

    init { |object_, methodKey_|
        object = object_;
        methodKey = methodKey_;
        if(object.respondsTo(methodKey)){ initialValue = object.perform(methodKey) }{ initialValue = false }; // hereby we allow for object which only have a setter method
        parameterValue = initialValue;
    }

    writeOutputValue {
        object.perform(methodKey.asSetter,outputValue);
    }

    updateOutputValue {
        ^outputValue = parameterValue;
    }

    parameterValue_ { |val|
        parameterValue = val.asBoolean;
        this.updateOutputValue;
        this.writeOutputValue;
    }
}