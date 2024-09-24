/* TODO
- make it possible to choose the referenceTrack
- make it possible to to have no filter */

LibraryConsole {
    var masterClock, <tempoFilter = 2, <tempoMultiplier = 1, <keyFilter = 4, <>activePlaylist, <activeTrackArrayFiltered, <>prelistenDeck, <referenceTrack, <>count = -1;

    *new { |prelistenDeck, masterClock|
        ^super.new.init(prelistenDeck, masterClock);
    }

    init { |prelistenDeck_, masterClock_|
        prelistenDeck = prelistenDeck_;
        masterClock = masterClock_;
        referenceTrack = TrackDescription.newDummy(125, 6);
    }

    tempoFilter_ { |val|
        tempoFilter = val;
        this.changed(\tempoFilter);
        this.filter;
    }

    tempoMultiplier_ { |val|
        tempoMultiplier = val;
        this.changed(\tempoMultiplier);
        this.filter;
    }

    keyFilter_ { |val|
        keyFilter = val;
        this.changed(\keyFilter);
        this.filter;
    }

    filter {
        var bpmLowBound, bpmUpBound, bpmMultiplier, currentBpm, keyTolerance;
        currentBpm = masterClock.tempo * 60;
        // the following defines the meaning of the tempoFilter parameter
        switch(tempoFilter.asInteger)
            { 1 } { bpmLowBound = 0; bpmUpBound = currentBpm}
            { 2 } { bpmLowBound = currentBpm - 10; bpmUpBound = currentBpm}
            { 3 } { bpmLowBound = currentBpm - 3; bpmUpBound = currentBpm + 3}
            { 4 } { bpmLowBound = currentBpm; bpmUpBound = currentBpm + 10}
            { 5 } { bpmLowBound = currentBpm; bpmUpBound = inf};

        // the following defines the meaning of the tkeyFilter parameter
        switch(keyFilter.asInteger)
            { 1 } { keyTolerance = 0.1 }
            { 2 } { keyTolerance = 1.1 }
            { 3 } { keyTolerance = 2.1 }
            { 4 } { keyTolerance = inf };

        activeTrackArrayFiltered = activePlaylist.asArray.removeNotUsable;
        activeTrackArrayFiltered = activeTrackArrayFiltered.filterBPM(bpmLowBound, bpmUpBound, tempoMultiplier);
        activeTrackArrayFiltered = activeTrackArrayFiltered.filterKey(referenceTrack.key.modulate(currentBpm/referenceTrack.bpm), currentBpm, keyTolerance);
        activeTrackArrayFiltered = activeTrackArrayFiltered.scramble;
        count = -1;
        "filtered playlist contains % tracks".format(activeTrackArrayFiltered.size).log(this);
    }

    setReferenceTrack {
        referenceTrack = prelistenDeck.track;
    }

    nextTrack_ { |direction = true|
        var increment;
        if(activeTrackArrayFiltered.isEmpty.not){
            if(direction){ increment = 1 }{ increment = -1};
            count = (count + increment).clip(0, activeTrackArrayFiltered.size - 1);
            ^prelistenDeck.loadTrack(activeTrackArrayFiltered[count]);
        }

    }

    loadPlaylist { |playlist|
        activePlaylist = playlist;
        this.filter;
    }

    activeTrackArray_ {
        // do nothing
        // something calls this method from somewhere, but I don't understand where; but if I delete it I run into an error
    }

    referenceTrack_ { |track|
        referenceTrack = track;
        this.filter;
    }
}