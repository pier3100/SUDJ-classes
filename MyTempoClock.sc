MyTempoClock : TempoClock {
    classvar <>barsPerPhrase = 8;
    var <backwardClock;
    var <bus;
    var <beatOfTurning = 0;
    var <tempo = 1;
    var <paused = false;
    var <tempoBeforePause;

	*new { arg tempoVal, beats, seconds, queueSize=256;
		^super.new.prInit(tempoVal, beats, seconds, queueSize);
	}

	prInit { arg tempoVal, beats, seconds, queueSize;
        backwardClock = TempoClock.new();
		queue = Array.new(queueSize);
		this.prStart(tempoVal, beats, seconds);
		all = all.add(this); 
        bus = Bus.control;
        this.tempo_(tempoVal);
	}

	tempo_ { arg newTempo;
        // normal clock only allows positive tempi, hence when tempo is zero, we do the bookkeeping ourselves, and when negative we run another clock, at tuerning point we do some bookkeeping, we differentiate between entering the turning point (because this allows to specify closely what happens if tempo is zero for a longer time), and existing, aswell as below and above
        if(newTempo.abs < 10e-6){ newTempo = 0 };
        if((newTempo <= 0) && (tempo > 0)){ //enter turning point from above
            beatOfTurning = super.beats;
            this.setTempoAtBeat(10e-08, this.beats); 
        };
        if((newTempo < 0) && (tempo >= 0)){ backwardClock.beats_(0) }; //exit turning point to below
        if((newTempo >= 0) && (tempo < 0)){ beatOfTurning = beatOfTurning - backwardClock.beats }; //enter turning point from below
        if((newTempo > 0) && (tempo <= 0)){ super.beats_(beatOfTurning) }; //exit turning point to above
        tempo = newTempo;
        if(tempo > 0){ this.setTempoAtBeat(tempo, this.beats) };
        if(tempo < 0){ backwardClock.tempo_(tempo.abs)};
        bus.setSynchronous(tempo);
		this.changed(\tempo);
	}

    beats {
        if(tempo == 0){ ^beatOfTurning };
        if(tempo < 0){ ^beatOfTurning - backwardClock.beats };
        if(tempo > 0){ ^super.beats };
    }

    beats_ { |beat|
        super.beats_(beat);
        if(tempo == 0){ beatOfTurning = beat }; 
    }

    pause {
        paused = true;
        tempoBeforePause = tempo.value;
        this.tempo_(0);
    }

    resume {
        paused = false;
        this.tempo_(tempoBeforePause);
    }

    phrase {
        // returns the current phrase
        ^this.beats.div(barsPerPhrase);
    }

    nextPhrase { |beat|
		// given a number of beats, determine number of beats at the next phrase line.
		if(beat.isNil){ beat = this.beats };
		^this.phrases2beats(this.beats2phrases(beat).ceil);
    }

    playNextPhrase { |task|
        this.schedAbs(this.nextPhrase, task); 
    }

    phrases2bars { |phrases|
        ^phrases*barsPerPhrase;
    }

    bars2phrases { |bars|
        ^bars/barsPerPhrase;
    }
    
    phrases2beats { |phrases|
        ^this.bars2beats(this.phrases2bars(phrases));
    }

    beats2phrases { |beats|
        ^this.bars2phrases(this.beats2bars(beats));
    }

    update { |theChanged, theChanger|
        if(theChanger==\tempo){
            this.tempo_(theChanged.tempo);
        }
    }
}