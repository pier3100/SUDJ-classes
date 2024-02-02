ServerControlLag : Object {
    classvar initialized = false, <dummyBus, <>smoothingTime = 0.2;

    *kr {arg key, initialValue = 0;
        // design is different as compared to modulation system in euro rack system, in some sense it is swapped, the modulator is connected to the dry/wet knob of a crossfade, and the reference value is fed into the crossfade
        var parameterValue, macroBusNr, sideChainModulater, sideChainRef;

        initialized.not.if({this.init});

        # parameterValue, macroBusNr = NamedControl.kr(key, [initialValue, dummyBus.index]); //the parameter value will be mapped to a bus which belongs to a physical controller
        # sideChainModulater, sideChainRef = In.kr(macroBusNr,2);//the macroBus signal will not be mapped but instead it will be read from the specified bus; because there is no syntax such as mapi, which we would need if we would want to use map, and we cannot conveniently unmap

        parameterValue = Lag.kr(parameterValue,smoothingTime);//added Lag to smooth the step changes in the control signal
        
        ^LinXFade2.kr(parameterValue, sideChainRef, sideChainModulater);
    }

    *init {
        this.initDummyBus;
        initialized = true;
    }

    *initDummyBus {
        dummyBus = Bus.control(numChannels: 2);
        dummyBus.setSynchronous(-1); //the first value of this bus is the crossfade, which should definitely be -1, i.e. take the dry signal
    }
}

ServerControl : Object {
    // no lag introduced, suitable for discrete signals
    classvar initialized = false, <dummyBus, <>smoothingTime = 0.2;

    *kr {arg key, initialValue = 0;
        // design is different as compared to modulation system in euro rack system, in some sense it is swapped, the modulator is connected to the dry/wet knob of a crossfade, and the reference value is fed into the crossfade
        var parameterValue, macroBusNr, sideChainModulater, sideChainRef;

        initialized.not.if({this.init});

        # parameterValue, macroBusNr = NamedControl.kr(key, [initialValue, dummyBus.index]); //the parameter value will be mapped to a bus which belongs to a physical controller
        # sideChainModulater, sideChainRef = In.kr(macroBusNr,2);//the macroBus signal will not be mapped but instead it will be read from the specified bus; because there is no syntax such as mapi, which we would need if we would want to use map, and we cannot conveniently unmap
        
        ^LinXFade2.kr(parameterValue, sideChainRef, sideChainModulater);
    }

    *init {
        this.initDummyBus;
        initialized = true;
    }

    *initDummyBus {
        dummyBus = Bus.control(numChannels: 2);
        dummyBus.setSynchronous(-1); //the first value of this bus is the crossfade, which should definitely be -1, i.e. take the dry signal
    }
}

/* ServerControl : Object {
    classvar initialized = false, <dummyBus, <>smoothingTime = 0.2;

    *kr {arg key, initialValue = 0;
        var parameterValue, macroBusNr, sideChainCrossfade, sideChainInput;

        initialized.not.if({this.init});

        # parameterValue, macroBusNr = NamedControl.kr(key, [initialValue, dummyBus.index]); //the parameter value will be mapped to a bus which belongs to a physical controller
        # sideChainCrossfade, sideChainInput = In.kr(macroBusNr,2);//the macroBus signal will not be mapped but instead it will be read from the specified bus; because there is no syntax such as mapi, which we would need if we would want to use map, and we cannot conveniently unmap

        parameterValue = Lag.kr(parameterValue,smoothingTime);//added Lag to smooth the step changes in the control signal
        
        ^LinXFade2.kr(parameterValue, sideChainInput, sideChainCrossfade);
    }

    *init {
        this.initDummyBus;
        initialized = true;
    }

    *initDummyBus {
        dummyBus = Bus.control(numChannels: 2);
        dummyBus.setSynchronous(-1); //the first value of this bus is the crossfade, which should definitely be -1, i.e. take the dry signal
    }
} */

LangControl : Object {
    // can be passed directly as a target
    classvar initialized = false, <dummyBus;
    var <initialValue, <>baseValue, <>parameterValue, sideChainCrossfade, sideChainInput, <>macroBus;

    *new { arg initialValue = 0;
        initialized.not.if({this.init});
        ^super.new.init(initialValue);
    }

    *init {
        this.initDummyBus;
        initialized = true;
    }

    *initDummyBus {
        dummyBus = Bus.control(numChannels: 2);
        dummyBus.setSynchronous(-1); //the first value of this bus is the crossfade, which should definitely be -1, i.e. take the dry signal
    }

    init { arg initialValue_;
        this.setMacroBus(dummyBus.index);
        initialValue = initialValue_;
        baseValue = initialValue;  //can be used to write a preset to
        parameterValue = initialValue;
    }

    value { |val|
        // this value method mainly performs three tasks: return the current output value (independent from the argument) and update the parameter value,and macromapping
        // this allows this value method to be used both when updating the parameter value (upon new controller data), and implement macro mapping
        // and when needing the current output value for processing and midi output;
        val.isNil.not.if({ 
            parameterValue = val;

            if(MidiHidSystem.macroToggle){ //here we implement the macroMapping behavior
                this.macroMappingBehavior;
            };
        });
        ^this.outputValue;
    }    

    sideChainCrossfade {
        this.macroValue;
        ^sideChainCrossfade;
    }

    sideChainInput {
        this.macroValue;
        ^sideChainInput;
    }

    outputValue {
        this.macroValue; //update sidechain values from the macro
        ^((0.5-(sideChainCrossfade/2))*parameterValue)+((0.5+(sideChainCrossfade/2))*sideChainInput);
    }

    macroValue {
        # sideChainCrossfade, sideChainInput = macroBus.getnSynchronous(2);
    }

    setMacroBus { |busNr|
        ^macroBus = Bus.new(rate: 'control', index: busNr, numChannels: 2);
    }

    macroMappingBehavior {
        this.setMacroBus(MidiHidSystem.macroBusNr);

    }
}