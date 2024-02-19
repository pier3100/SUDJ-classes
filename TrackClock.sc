/*
TODO

DONE
- tempo/phase syncing
    -> master tempo is set, just like in Traktor
    -> dependancy (but dependant unaware of its master)
- phase syncing
    -> always
- design
    - activate/deactive sync deck with master (when in sync tempo changes of the master ar trasfered to the deck, and perhaps tempo changes to the deck or rewired to the master [which then eventually comees back to the deck itself via the sync] )
        - possibly we need a watchdog PLC which makes sure phase remains in sync
    - a button to instantanously set the deck as the master tempo (that is: sync at this moment the master to the current deck, and then slave this deck to the master)
    - master clock is a LinkClock
- I think it makes more sense to let the beats value of the TrackClock be determining for the DJDeck, so that would entail that if you set the beats value to another value, the DJdeck should follow. So instead of jumpToBeat calling clock.beats, we will make the DJdeck dependent upon the beats value, so we get beats calling jumpToBeat (by dependancy and .update); this makes sense as we are syncing the clocks, and the DJ deck needs to make sure its track is in sync with its clock.

*/

TrackClock : TempoClock {
    // a tempo clock which allows non-positive tempi, and offers functionality for syncing clocks, can be paused, and features a bus which can be used in synths
    classvar <>barsPerPhrase = 8;
    var <backwardClock;
    var <bus;
    var <beatOfTurning = 0;
    var <tempo = 1;
    var <paused = false;
    var <>fallbackTempo;
    var <sync = false, <>master;

	*new { |tempoVal = 1, beats, seconds, queueSize=256|
		^super.new.prInit(tempoVal, beats, seconds, queueSize);
	}

	prInit { |tempoVal, beats, seconds, queueSize|
        backwardClock = TempoClock.new();
		queue = Array.new(queueSize);
		this.prStart(tempoVal, beats, seconds);
		all = all.add(this); 
        bus = Bus.control;
        this.tempo_(tempoVal);
	}

	tempo_ { |newTempo|
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
        ^this;
	}

    tempoInterface_ { |newTempo|
        // use this method to set the tempo from user side, it takes account of their being a master tempo to which we are being synced; and it deals with  being paused
        if(paused){
            fallbackTempo = newTempo; // if we are paused we the current tempo is 0, we assume one whishes to change the fallbacktempo instead
        }{
            if(sync){ master.tempo_(newTempo) }{ this.tempo_(newTempo) }; // is we are being synced we forward the requested tempo change to the master (which sends it right back via the dependency route)
        }
        
    }

    tempoInterface {
        // returns the tempo, and the fallBacktempo if paused
        var output;
        if(paused){
            output = fallbackTempo;
        }{
            output = tempo;
        }
        ^output;           
    }

    beats {
        if(tempo == 0){ ^beatOfTurning };
        if(tempo < 0){ ^beatOfTurning - backwardClock.beats };
        if(tempo > 0){ ^super.beats };
    }

    beats_ { |beat|
        super.beats_(beat);
        if(tempo == 0){ beatOfTurning = beat }; 
        this.changed([\beats, beat]); // beat jumping is tricky, as the logical time is kept constant during function calls;
    }

    pause {
        // to be paused means to have a tempo of 0
        if(paused.not){
            paused = true;
            fallbackTempo = tempo.value; // this will be tempo we go back to once resuming playing
            this.tempo_(0);
        }
        ^this;
    }

    resume {
        if(paused){
            paused = false;
            if(sync){ this.tempo_(master.tempo); this.phaseSync; }{ this.tempo_(fallbackTempo) }; // if we are synced we take the tempo from the master when resuming (instead of our tempofrom before the pause), and set the phase right
        }
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
            if(paused.not){ this.tempo_(theChanged.tempo) }; // we only update the tempo from the master side, if we are not paused; because if we are paused we have a tempo of zero
        }
    }

    activateSync { |master_|
        // this allows a Tempo to be synced to another tempo, from the perspective of the one being slaved
        // the actual syncing is taken care by, by the dependancy system, which calls the update method as implemented above
        // you can tell to which master to sync, or sync to the allready set master 
        sync = true;
        master_ !? {
            if(master_ != master){ master.removeDependant(this) }; // we need to kill our ties to the old master
            master = master_; // we store it such that there is no confusion to whom we are being synced; hence we can easily desync
        };
        master.addDependant(this);
        this.tempo_(master.tempo);
        if(paused.not){ this.phaseSync }; // we will phase sync when hitting .resume in case we are paused
    }

    deactiveSync {
        sync = false;
        master.removeDependant(this);
    }

    syncMasterToMe { |master_|
        master_ !? {
            if(master_ != master){ master.removeDependant(this) }; // we need to kill our ties to the old master
            master = master_; // we store it such that there is no confusion to whom we are being synced; hence we can easily desync
        };
        master.dependants.asArray.do({ |item| item.deactiveSync }); // stop all other clocks from syncing to the master including me
        master.tempo = this.tempo; // now sync the master to me at this instance
        master.beat = this.beat; 
        this.activateSync; // slave/sync me to the master
    }

    phaseSync {
        var fracMaster, fracSlave;
        fracMaster = master.beats.frac;
        fracSlave = this.beats.frac;
        // the following makes sure we will never jump backward in time for the sake of phasesync, but always forward, except if it is not noticeable, that is less than 0.05 sec
        if(fracMaster >= (fracSlave - (0.05 * this.tempo))){
            this.beats_(this.beats.floor + fracMaster);
        }{
            this.beats_(this.beats.floor + fracMaster + 1);
        }
        
    }
}