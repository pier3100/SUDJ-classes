PLC {
    // executes a list of functions periodically, calls to the functions are surpressed when the function active evaluates false
    var <>funcList, <clock, <routine, <active = true;

    *new { |tempo = 1, active|
        ^super.new.init(tempo, active);
    }

    init { |tempo_, active_|
        funcList = FunctionList.new;
        clock = TempoClock.new(tempo_).permanent = true;//create a TempoClock with desired tempo, and make sure it survives Cmd+.
        active_ !? {active = active_ };

        routine = Routine{
            inf.do{ |i|
                clock.timeToNextBeat.wait; //wait till next cyle start
                if(active.value){ funcList.value };     //run all functions
                0.1.wait;       //should be lower then cycle length, is required in order to make sure not two cycles are computed directly after each other
            };
        };

        clock.play(routine,quant:1); // schedule the routine on the clock

        CmdPeriod.add(this);
    }

    cmdPeriod {
        clock.play(routine,quant:1); // make sure to reschedule the routine when the cmdPeriod is being pressed (which aborts the scheduled tasks)
    }

    tempo_ { |value|
        clock.tempo_(value);
    }

    tempo {
        ^clock.tempo;
    }

    add { |function|
        funcList.addFunc(function);
    }

    remove { |function|
        funcList.removeFunc(function);
    }

    reset {
        funcList.array = nil;
    }

}

/* PLC {
    var <>funcList1, <>funcList2, <>funcList3, <clock, <routine;

    *new {arg tempo = 1;
        ^super.new.init(tempo);
    }

    init {arg tempo_;
        funcList1 = FunctionList.new;
        funcList2 = FunctionList.new;
        funcList3 = FunctionList.new;

        clock = TempoClock.new(tempo_).permanent = true;//create a TempoClock with desired tempo, and make sure it survives Cmd+.

        routine = Routine{
            inf.do{ |i|
                clock.timeToNextBeat.wait; //wait till next cyle start
                funcList1.value;      //run all functions
                funcList2.value;
                funcList3.value;
                0.1.wait;       //should be lower then cycle length, is required in order to make sure not two cycles are computed directly after each other
            };
        };

        clock.play(routine,quant:1); // schedule the routine on the clock

        CmdPeriod.add(this);
    }

    cmdPeriod {
        clock.play(routine,quant:1); // make sure to reschedule the routine when the cmdPeriod is being pressed (which aborts the scheduled tasks)
    }

    tempo_ {arg value;
        clock.tempo_(value);
    }

    tempo {
        ^clock.tempo;
    }

} */