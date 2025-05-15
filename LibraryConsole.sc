/* TODO
- make it possible to choose the referenceTrack
- make it possible to to have no filter */

LibraryConsole {
    var masterClock;
    var <tempoFilter = 2, <tempoMultiplier = 1, <keyFilter = 4, <minorMajorFilterCoefficient = 2;
    var <>activePlaylist, <activeTrackArrayFiltered, <>prelistenDeck, <referenceTrack, <>count = -1;
    var <bufferArray; 

    *new { |prelistenDeck, masterClock|
        ^super.new.init(prelistenDeck, masterClock);
    }

    init { |prelistenDeck_, masterClock_|
        prelistenDeck = prelistenDeck_;
        masterClock = masterClock_;
        referenceTrack = TrackDescription.newDummy(125, 6);
        bufferArray = Array.fill(5, {Buffer.alloc(Server.default, Server.default.sampleRate * 200, 2)});
    }

    tempoFilter_ { |val|
        tempoFilter = val;
        this.changed(\tempoFilter);
        this.filter;
        this.installPlaylist;
    }

    tempoMultiplier_ { |val|
        tempoMultiplier = val;
        this.changed(\tempoMultiplier);
        this.filter;
        this.installPlaylist;
    }

    keyFilter_ { |val|
        keyFilter = val;
        this.changed(\keyFilter);
        this.filter;
        this.installPlaylist;
    }

    minorMajorFilterCoefficient_ { |val|
        minorMajorFilterCoefficient = val;
        this.changed(\minorMajorFilterCoefficient);
        this.filter;
        this.installPlaylist;
    }

    filter {
        var bpmLowBound, bpmUpBound, bpmMultiplier, currentBpm, keyToleranceDistanceLow, keyToleranceDistanceHigh, minorMajorFilter;
        currentBpm = masterClock.tempo * 60;
        // the following defines the meaning of the tempoFilter parameter
        switch(tempoFilter.asInteger)
            { 1 } { bpmLowBound = 0; bpmUpBound = currentBpm + 3}
            { 2 } { bpmLowBound = currentBpm - 10; bpmUpBound = currentBpm + 3}
            { 3 } { bpmLowBound = currentBpm - 3; bpmUpBound = currentBpm + 3}
            { 4 } { bpmLowBound = currentBpm - 3; bpmUpBound = currentBpm + 10}
            { 5 } { bpmLowBound = currentBpm - 3; bpmUpBound = inf};

        // the following defines the meaning of the keyFilter parameter
        switch(keyFilter.asInteger)
            { 1 } { keyToleranceDistanceLow = 0; keyToleranceDistanceHigh = 0.1 }
            { 2 } { keyToleranceDistanceLow = 1; keyToleranceDistanceHigh = 1.1 }
            { 3 } { keyToleranceDistanceLow = 0; keyToleranceDistanceHigh = 2.1 }
            { 4 } { keyToleranceDistanceLow = 0; keyToleranceDistanceHigh = inf };

        switch(minorMajorFilterCoefficient.asInteger)
            { 1 } { minorMajorFilter = "minor" }
            { 2 } { minorMajorFilter = nil }
            { 3 } { minorMajorFilter = "major" };

        activeTrackArrayFiltered = activePlaylist.filterBPM(bpmLowBound, bpmUpBound, tempoMultiplier);
        activeTrackArrayFiltered = activeTrackArrayFiltered.filterKey(referenceTrack.key.modulate(currentBpm/referenceTrack.bpm), currentBpm, keyToleranceDistanceLow, keyToleranceDistanceHigh, minorMajorFilter);
    }

    numberOfTracks {
        ^activeTrackArrayFiltered.size;
    }

    setReferenceTrack { |track|
        referenceTrack = track ? prelistenDeck.track;
        this.filter;
        this.installPlaylist;
        this.nextTrack_;
    }

    oldnextTrack_ { |direction = true|
        var increment;
        if(activeTrackArrayFiltered.isEmpty.not){
            if(direction){ 
                increment = 1 
            }{ 
                increment = -1
            };
            count = (count + increment).clip(0, activeTrackArrayFiltered.size - 1);
            ^prelistenDeck.loadTrack(activeTrackArrayFiltered[count]);
        }
    }

    nextTrack_ { |direction = true|
        var tempCount;
        if(activeTrackArrayFiltered.isEmpty.not){
            if(direction){ 
                tempCount = count + 1;
                if(tempCount <= (activeTrackArrayFiltered.size - 1)){
                    // we restrict our selves to the size of the array
                    count = tempCount;
                    for(0,3){ |i|
                        bufferArray[i] = bufferArray[i + 1];
                    };
                    if(count + 2 <= (activeTrackArrayFiltered.size - 1)){
                        bufferArray[4] = activeTrackArrayFiltered[count + 2].loadBuffer;
                    };
                }
            }{ 
                tempCount = count - 1;
                if(tempCount >= 0){
                    // we restrict our selves to the size of the array
                    count = tempCount;
                    for(4,1){ |i|
                        bufferArray[i] = bufferArray[i - 1];
                    };
                    if(count - 2 >= 0){
                        bufferArray[0] = activeTrackArrayFiltered[count - 2].loadBuffer;
                    };
                };
            };
            ^prelistenDeck.loadTrackFromBuffer(activeTrackArrayFiltered[count], bufferArray[2]);
        }
    }

    previousTrack_ {
        this.nextTrack_(false);
    }

    installPlaylist {
        activeTrackArrayFiltered = activeTrackArrayFiltered.scramble;
        count = -1;
        "filtered playlist contains % tracks".format(activeTrackArrayFiltered.size).log(this);
        if(activeTrackArrayFiltered.isEmpty.not){
            // we initialize the buffer array for this playlist, we keep the first three (0, 1, 2) empty, because these are use for the previous tweo tracks, and we fill the third one upon calling the first nextTrack, so we only fill spot 3,4
            bufferArray[3] = activeTrackArrayFiltered[0].loadBuffer; 
            if(activeTrackArrayFiltered.size >= 2){
                bufferArray[4] = activeTrackArrayFiltered[1].loadBuffer; 
            };
        };
        this.changed(\numberOfTracks);
    }

    loadPlaylist { |playlist|
        activePlaylist = playlist.asTrackArray.removeNotUsable;
        this.filter;
        this.installPlaylist;
    }

    activeTrackArray_ {
        // do nothing
        // something calls this method from somewhere, but I don't understand where; but if I delete it I run into an error
    }

    reset {
        this.setReferenceTrack;
    }
}