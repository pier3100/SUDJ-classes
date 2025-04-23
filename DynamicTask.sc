DynamicTask {
    // the target can be scheduled and rescheduled
    var <clockUsed, <cancel = true, <>target, <schedAtTime;

    *new { |clock, target|
        ^super.new.init(clock, target);
    }

    init { |clock, target_|
        clockUsed = clock;
        target = target_;
    }

    cancel_ { |bool|
        cancel = bool ? true;
    }

    schedAbs { |time, clock|
        cancel = false;
        clock !? { clockUsed = clock };
        clockUsed.schedAbs(time, this);
        schedAtTime = time;
    }

    sched { |delta, clock|
        var currentTime;
        clock !? { clockUsed = clock };
        case
        { clockUsed.class == TempoClock }{ currentTime = clockUsed.beats; this.schedAbs(currentTime + delta, clockUsed) }
        { clockUsed == SystemClock }{ currentTime = clockUsed.seconds; this.schedAbs(currentTime + delta, clockUsed) };
        //{ clockUsed == AppClock }{ clockUsed.sched(delta, this); schedAtTime = clockUsed.seconds + delta }; // time comparison does not work with awake
    }

    awake { |time, seconds, clock|
        if((time == schedAtTime) && cancel.not){ 
            cancel = true;
            ^target.awake(time, seconds, clock); 
        }
    }
}