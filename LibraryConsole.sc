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
        // tolerance should be 1, 2, or 3; wherby 3 is no constraint, and 1 is tracks which match closely
        // TODO: perhaps change to a value between 0, 1, that way we can also map it to a slider o.a.
        var toleranceRounded;
        toleranceRounded  = tolerance.round.asInteger;
        activeTrackArrayFiltered = switch(toleranceRounded)
            { 1 } { activeTrackArray.filterBPM(referenceTrack.bpm - (tolerance * 4),referenceTrack.bpm + (tolerance * 4), toleranceRounded).filterKey(referenceTrack.key, referenceTrack.bpm, tolerance).scramble }
            { 2 } { activeTrackArray.filterBPM(referenceTrack.bpm - (tolerance * 4),referenceTrack.bpm + (tolerance * 4), toleranceRounded).filterKey(referenceTrack.key, referenceTrack.bpm, tolerance).scramble }
            { 3 } { activeTrackArray.scramble };
        ^activeTrackArrayFiltered;
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