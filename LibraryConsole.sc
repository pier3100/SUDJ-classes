/* TODO
- make it possible to choose the referenceTrack
- make it possible to to have no filter */

LibraryConsole {
    var masterClock, <>tempoFilter = 2, <>tempoMultiplier = 1, <>keyFilter = 3, <>activePlaylist, <activeTrackArrayFiltered, <>prelistenDeck, <>referenceTrack, <>count = -1;

    *new { |prelistenDeck, masterClock|
        ^super.new.init(prelistenDeck, masterClock);
    }

    init { |prelistenDeck_, masterClock_|
        prelistenDeck = prelistenDeck_;
        masterClock = masterClock_;
        referenceTrack = TrackDescription.newDummy(125, 6);
    }

    filter {
        var bpmLowBound, bpmUpBound, bpmMultiplier, currentBpm;
        currentBpm = masterClock.tempo * 60;
        // the following defines the meaning of the tempoFilter parameter
        switch(tempoFilter)
            { 1 } { bpmLowBound = 0; bpmUpBound = currentBpm}
            { 2 } { bpmLowBound = currentBpm - 10; bpmUpBound = currentBpm}
            { 3 } { bpmLowBound = currentBpm - 3; bpmUpBound = currentBpm + 3}
            { 4 } { bpmLowBound = currentBpm; bpmUpBound = currentBpm + 10}
            { 5 } { bpmLowBound = currentBpm; bpmUpBound = inf};

        // the following defines the meaning of the tkeyFilter parameter

        activeTrackArrayFiltered = activePlaylist.asArray.filterBPM(bpmLowBound, bpmUpBound, tempoMultiplier);
        //activeTrackArrayFiltered = activeTrackArrayFiltered.filterKey
        activeTrackArrayFiltered = activeTrackArrayFiltered.scramble;
        count = -1;
        if(activeTrackArrayFiltered.isEmpty){ "filtered playlist is empty".log(this) };
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
}