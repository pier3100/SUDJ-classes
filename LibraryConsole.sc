/* TODO
- make it possible to choose the referenceTrack
- make it possible to to have no filter */

LibraryConsole {
    var <>activeTrackArray, <activeTrackArrayFiltered, <>prelistenDeck, <>referenceTrack, <>count = -1;

    *new { |prelistenDeck|
        ^super.new.init(prelistenDeck);
    }

    init { |prelistenDeck_|
        prelistenDeck = prelistenDeck_;
        referenceTrack = TrackDescription.newDummy(125, 6);
    }

    filter { |tolerance|
        var toleranceRounded;
        toleranceRounded  = tolerance.round;
        // for compatibility with filterBPM, we make sure it is not 3
        if(toleranceRounded == 3){ 
            if(toleranceRounded <= 3){
                toleranceRounded = 2;
                }{
                toleranceRounded = 4;
            }
        }
        ^activeTrackArrayFiltered = activeTrackArray.filterBPM(referenceTrack.bpm - (tolerance * 4),referenceTrack.bpm + (tolerance * 4), toleranceRounded).filterKey(referenceTrack.key, referenceTrack.bpm, tolerance).randomize;
    }

    setReferenceTrack {
        referenceTrack = prelistenDeck.track;
    }

    nextTrack_ { |direction = true|
        var increment;
        if(direction){ increment = 1 }{ increment = -1};
        count = (count + increment).clip(0, activeTrackArrayFiltered.size - 1);
        ^prelistenDeck.loadTrack(activeTrackArrayFiltered[count]);
    }
}