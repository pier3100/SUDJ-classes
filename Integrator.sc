ClockedIntegrator {
    var delta, <>slope = 0, <> value = 0;

    *new {arg delta_; //delta 1/tempo, it is the period in s, wheras tempo is in Hz
		^super.new.init(delta_);
	}

	init {arg delta_;
		delta = delta_;
	}

    next {
        ^value = value + ( slope * delta );
    }
}