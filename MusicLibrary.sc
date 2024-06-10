/* 
TODO
- test HID support with joystick, and if this works but a barcode scanner

DONE
- Lookup tracks based on TraktorID instead of path, because path might change
- speedup updating from traktor by looking at Traktor modified date
- test updating from Traktor
- use a collection instead of an array, because we can then use the traktor id for fast lookup; or perhaps better make a seperate lookup table, which just contains all ids and array index; we cannot modify an existing method from array or its super classes, because these classes do not assume unique methods, so do not implement a hash functions, so it won't be fast
    - for playlists: make an array of the id's (which can then be directly used for fast lookup)
 */

MusicLibrary {
    var <>tracks, <>playlists, <barcodeDictionary;

    *load { |path|
        ^Object.readTextArchive(path);
    }

    *loadFromTraktor{ |libraryPath, traktorLibraryPath, forceLoad = false|
        // load the archived library and update it from the Traktor collection if needed, if no archive exists, create new library from scratch from the Traktor collection
        var instance;
        if(File.exists(libraryPath) && forceLoad.not){
            instance = this.load(libraryPath);
            if(File.mtime(traktorLibraryPath) > File.mtime(libraryPath)){ // if traktor has recently updated the collection, we need to update our collection as well
                var collectionText;
                "Updating Traktor library".log(this);
                collectionText = File.readAllString(traktorLibraryPath);
                instance.updateTracksFromTraktor(collectionText, Date.rawSeconds(File.mtime(libraryPath)).asSortableString);
                Library.put(\musicLibrary,instance); // should happen before loading the playlists, because the Playlist.new method lookup the tracks in the musicLibrary
                instance.loadPlaylistsFromTraktor(collectionText); // overwrite all playlists
            }{
                "Reusing existing music library, no need to update".log(this);
                Library.put(\musicLibrary,instance); // should happen before loading the playlists, because the Playlist.new method lookup the tracks in the musicLibrary
            }
        }{
            "Loading library from Traktor from scratch".log(this);
            instance = this.newFromTraktor(traktorLibraryPath);
        };
        ^instance;
    }

    *newFromTraktor { |path|
        var instance, collectionText;
        collectionText = File.readAllString(path);
        instance = super.new;
        instance.loadTracksFromTraktor(collectionText);
        Library.put(\musicLibrary,instance); // should happen before loading the playlists, because the Playlist.new method lookup the tracks in the musicLibrary
        instance.loadPlaylistsFromTraktor(collectionText);
        ^instance;
    }

    updateTracksFromTraktor { |collectionText, dateModifiedLibrary|
        // we update each entry in the collection
        // this will be a slow function as we can't assume the indexes stay the same, hence we need to lookup each entry; maybe we can apply some smart ordering which makes this faster
        var tracksText, numberTracks, previousIndex = 0;
        //load tracks
        tracksText = collectionText.findInBetween("<COLLECTION", "</COLLECTION>").string;
        numberTracks = collectionText.lookup("COLLECTION ENTRIES").asInteger;
        for(0,(numberTracks-1)){ |i|
            var substring, id, string, track, dateModifiedTrack;
            substring = tracksText.findInBetween("<ENTRY", "</ENTRY>", previousIndex);
            previousIndex = substring.endIndex;
            string = substring.string;       
            dateModifiedTrack = string.lookup("MODIFIED_DATE").replace("\/");
            if(dateModifiedTrack > dateModifiedLibrary){ // we only need to load the track if it is updated recently; we compare the strings, which are suited for this purpose
                id = string.lookup("AUDIO_ID");
                track = this.findId(id); // retrieve track in library by looking it up by id
                if(track.isNil){ 
                    tracks.add(TrackDescription.newFromTraktor(string));
                     "track % appended to tracklist: \t %".format(i, substring.string).log(this);
                    }{
                    track.fromTraktor(string); 
                    if(track.usable.not){ "track % not usable bit still in tracklist: \t %".format(i, substring.string).log(this) }; // this is a bit ugly because if the new Track information 
                }
            }
        };
    }

    loadTracksFromTraktor { |collectionText|
        //load tracks
        var tracksText, numberTracks, previousIndex = 0;
        tracksText = collectionText.findInBetween("<COLLECTION", "</COLLECTION>").string;
        numberTracks = collectionText.lookup("COLLECTION ENTRIES").asInteger;
        tracks = Array.newClear(numberTracks);
        for(0, (numberTracks - 1)){ |i|
            var substring, track;
            substring = tracksText.findInBetween("<ENTRY", "</ENTRY>", previousIndex);
            previousIndex = substring.endIndex;
            track = TrackDescription.newFromTraktor(substring.string);
            if(track.usable){ tracks.put(i, track) }{ "track % not usable: \t %".format(i, substring.string).log(this) };
        };
        tracks.removeNil;
        ^tracks;
    }

    loadPlaylistsFromTraktor { |collectionText|
        //load playlists
        var playlistsText, playlistSubstring, playlistFolderSubstring, previousIndexPlaylistFolder = 0, previousIndexPlaylist;
        playlistsText = collectionText.findInBetween("<PLAYLISTS>", "</PLAYLISTS>").string;
        playlists = MultiLevelIdentityDictionary.new;

        // we start by opening the root folder and then recursively iterate
        this.loadFolder(playlistsText, 0, playlists);

        this.barcodeDictionary_;
        ^playlists;
    }

    loadFolder { |playlistsText, startIndex, parentDictionary|
        var previousIndex, thisFolder, nodeTypeSubstring, nodeType, nodeCountSubstring, nodeCount;
        previousIndex = startIndex;
        thisFolder = IdentityDictionary.new;
        parentDictionary.put(playlistsText.findInBetween("NAME=\"","\"", startIndex).string.asSymbol, thisFolder); // add a new level/folder to out dictionary (folder system)
        
        nodeCountSubstring = playlistsText.findInBetween("<SUBNODES COUNT=\"","\"", startIndex);
        nodeCount = nodeCountSubstring.string.asInteger;
        previousIndex = nodeCountSubstring.endIndex;
        for(0, (nodeCount - 1)){ |i|
            nodeTypeSubstring = playlistsText.findInBetween("NODE TYPE=\"", "\"", previousIndex);
            nodeType = nodeTypeSubstring.string;

            if(nodeType == "FOLDER"){
                previousIndex = this.loadFolder(playlistsText, nodeTypeSubstring.endIndex, thisFolder);
            }{
                if(nodeType == "PLAYLIST"){
                    var playlist, playlistSubstring;
                    playlistSubstring = playlistsText.findInBetween("<NODE TYPE=\"PLAYLIST\"","</NODE>", (nodeTypeSubstring.startIndex - 12));
                    playlist = Playlist.newFromTraktor(playlistSubstring.string, this);
                    playlist.isNil.not.if({thisFolder.put(playlist.name.asSymbol, playlist)});
                    previousIndex = playlistSubstring.endIndex;
                }{
                    // if not a folder or playlist, ignor the node
                    previousIndex = nodeTypeSubstring.endIndex; // update the index such that we continue to the next node
                };
            };
        };
        ^previousIndex;

    }

    barcodeDictionary_ {
        // we make an identity dictionary which maps the barcode ID to the playlist itself, we assume the ID is unique, if non we give a warning
        barcodeDictionary = IdentityDictionary.new(playlists.size);
        playlists.leafDo({ |key, item| 
            barcodeDictionary.putGet(item.barcodeId, item) !? { "WARNING: non-unique barcode ID".log(this) };
        })
    }

    exportBarcodes { |path, folderNames|
        // export a .csv file with in the first collumn the barcode, and in the second collumn the name of playlist
        // it is important that we give the playlists extended names (so we should change the name = blabla line): names which contain the folders name to (example: "E3: UK hardcore" instead of "UK hardcode")
        var csvFile, string, folder, playlist;
        csvFile = File.new(path, "w");
        csvFile.write("barcode; name; \n");
        for(0, (folderNames.size - 1)){ |i|
            folder = playlists.at("$ROOT".asSymbol, folderNames[i].asSymbol);
            folder.do({ |playlist|
                string = playlist.barcodeId.asString.barcodeId2EAN13(1) ++ "; " ++ folderNames[i].asString ++ ":: " ++ playlist.name ++ "; \n"; // we need to convert the barcode ID to an actual barcode
                csvFile.write(string);
            });
        };
        csvFile.close;
    }

    asArray {
        ^tracks;
    }

    findTrackByTitle { |string|
        // rewrite using .selectIndices(function)
        var result = Array.new(this.tracks.size);
        tracks.do({ |item, i| if(item.title.find(string).isNil.not){ result.add(item) } });
        ^result;
    }

    findPath { |path|
        ^tracks.select({ |item| item.path == path }).[0];
        //^tracks[this.findPathIndices(path)];
    }

    findId { |id|
        ^tracks.select({ |item| item.id == id }).[0];
    }

    findIdIndices { |id|
        ^tracks.selectIndices({ |item| item.id == id });
    }

    findPathIndices { |path|
        ^tracks.selectIndices({ |item, i| item.path == path });
    }

    findPlaylistByTitle { |string|
        ^playlists.select({ |item| item.name.find(string).isNil.not });
    }

    store { |path|
        this.writeArchive(path);
    }
}

Playlist {
    var <>name, <>uuId, <>barcodeId, <>tracksIndex;

    *newFromTraktor { |playlistString|
        ^super.new.initFromTraktor(playlistString);
    }

    initFromTraktor { |playlistString|
        var playlistLength, previousIndex = 0, output;
        name = playlistString.lookup("NAME");
        uuId = playlistString.lookup("UUID");
        barcodeId = this.uuId2barcodeId(uuId);
        playlistLength = playlistString.lookup("PLAYLIST ENTRIES").asInteger;
        if(playlistLength == 0){
            output = nil;
        }{
            tracksIndex = Array.newClear(playlistLength);
            for(0,(playlistLength-1)){ |i|
                var substring, trackIndex, path;
                substring = playlistString.findInBetween("<ENTRY", "</ENTRY>", previousIndex);
                previousIndex = substring.endIndex;
                path = substring.string.lookup("KEY").formatPlain.traktorPath2path;
                trackIndex = Library.at(\musicLibrary).findPathIndices(path)[0];
                tracksIndex.put(i, trackIndex);
                trackIndex ?? { "track missing in %: \t %".format(name, path).log(this) }
            };
            tracksIndex.removeNil;
            output = this;
        };
        ^output;
    }

    uuId2barcodeId { |uuIdString|
        // the uuID is expected to be 32 characters long, the barcodeID can only be 9 digits long, furthermore uuID is hexidecimal, while barcodeID should be decimal (since we can only communicate a decimal number via hidapi)
        // we take a selection of digits
        ^(uuIdString.digit*(2**Array.series(uuIdString.size,0,1))).sum.asString.copyRange(0,8).asSymbol;
    }

    asArray {
        ^Library.at(\musicLibrary).tracks.at(tracksIndex);
    }

    randomTrack { 
        ^Library.at(\musicLibrary).tracks[tracksIndex.rand];
    }
}

Key {
    var <scale, <rootNote;
    
    *new { |scale, rootNote|
        ^super.newCopyArgs(scale, rootNote);
    }

    *newFromTraktor { |keyNumber|
        ^super.new.initFromTraktor(keyNumber);
    }

    initFromTraktor { |keyNumber|
        if(keyNumber>=12){ scale = Scale.major; rootNote = keyNumber - 12 }{ scale = Scale.minor; rootNote = keyNumber };
    }

    modulate { |ratio|
        //returns a new key which you get of you speed up/down the record with ratio = newSpeed/oldSpeed
        ^Key.new(scale,(rootNote + ((ratio.log / 2.log) * 12)).mod(12));
    }

    compatibility { |key|
        // should describe how well keys fit together according to circle of fiths, lower score is better, 0 = exactly the same key, 1 = exactly a fifth a part
        // for now it makes sense to me to apprach this from a traditional tone/key perspective, since this is by design a ring, i.e. note g and a are next to each other; while if we start using pitches we need to define such a concept a new for ourselves
        var score;
        if(scale == key.scale){ 
            score = this.compatibilityCircleOfFifthsMajorKeys(rootNote,key.rootNote);
        }{
            var shiftedNote;
            if(scale==Scale.minor){
                shiftedNote = rootNote + 2; // for the minor scale we add + 2, now we can compare the keys as if they were both major, according to the circle of fifths
                score = this.compatibilityCircleOfFifthsMajorKeys(shiftedNote,key.rootNote);
            }{ //in this case necessarily the other key is minot
                shiftedNote = key.rootNote + 2;
                score = this.compatibilityCircleOfFifthsMajorKeys(rootNote,shiftedNote);
            }
        }
        ^score;        
    }

    compatibilityCircleOfFifthsMajorKeys { |rootNoteA, rootNoteB|
        // only makes sense if both keys are major
        var noteDif, fifthScore, penalty, finalScore;
        noteDif = rootNoteA.moddif(rootNoteB,12);
        fifthScore = noteDif.round.modSolve(5,12); // it outputs the distance along the circle of fifths
        penalty = 2 * (noteDif.floor - noteDif).abs * (noteDif.ceil - noteDif).abs * 5; // we interpolate the penalty which you get wrt bigger key and smaller key differnce; a penalty from deviating of being perfect; where the multiplier of 5 is chosen such that for notes next to each other you get a score of 5, which makes sense because in the circle of fiths that's also there distance
        ^finalScore = fifthScore + penalty;
    }
}

TrackDescription : SoundFile {
    //make a child from SoundFile
    var <usable = true, <title, <artist, <key, <bpm, <gridOffset, <>userInducedGridOffset = 0, <id, <preceivedDb;

    *newDummy { |bpm, keyNumber|
        ^super.openRead(Platform.resourceDir +/+ "sounds/a11wlk01.wav").init(bpm, keyNumber);
    }

    *newFromTraktor { |string|
        ^super.new.fromTraktor(string);
    }

    init { |bpm_, keyNumber_|
        bpm = bpm_;
        key = Key.newFromTraktor(keyNumber_);
    }

    fromTraktor { |string|
        path = (string.lookup("VOLUME")++string.lookup("LOCATION DIR")++string.lookup("FILE")).formatPlain.traktorPath2path;
        title = string.lookup("TITLE").formatPlain;
        artist = (string.lookup("ARTIST") ? "EMPTY").formatPlain;
        key = string.lookup("MUSICAL_KEY VALUE");
        if(key.isNil){ usable = false }{ key = Key.newFromTraktor(key.asInteger) };
        bpm = string.lookup("TEMPO BPM");
        if(bpm.isNil){ usable = false }{ bpm = bpm.asFloat };
        gridOffset = (string.lookup("START") ? 0).asFloat/1000;
        id = string.lookup("AUDIO_ID");
        preceivedDb = string.lookup("PERCEIVED_DB");
        if(preceivedDb.isNil){ usable = false }{ preceivedDb = preceivedDb.asFloat };
        ^this;
    }

    loadBuffer { |action|
        var buffer;
        this.openRead;
        buffer = this.asBuffer(action: action);
        this.close;
        ^buffer;
    }

    asBuffer { |server, action|
		var buffer, rawData;
		server = server ? Server.default;
		if(server.serverRunning.not) { Error("SoundFile:asBuffer - Server not running.").throw };
		if(this.isOpen.not) { Error("SoundFile:asBuffer - SoundFile not open.").throw };
		if(server.isLocal) {
			buffer = Buffer.read(server, path, action: action);
		} {
			forkIfNeeded {
				buffer = Buffer.alloc(server, numFrames, numChannels);
				rawData = FloatArray.newClear(numFrames * numChannels);
				this.readData(rawData);
				server.sync;
				buffer.sendCollection(rawData, action: action, wait: -1);
			}
		};
		^buffer
	}

    keyAtBPM { |playBPM|
        ^key.switched(playBPM/bpm);
    }

}

Substring {
    var <>string;
    var <>startIndex;
    var <>endIndex;

    *new { |string, startIndex = 0, endIndex = 0|
        ^super.new.init(string, startIndex, endIndex);
    }

    init { |string_, startI, endI|
        string = string_;
        startIndex = startI;
        endIndex = endI;
    }

}

+ String {
    findInBetween { |stringA, stringB, offset = 0|
    // we output a subString such that subString.string is the string inbetween stringA and stringB, all contained in the receiver string
        var indexA, indexB, start, output;
            indexA = this.find(stringA, offset: offset);
            indexA !? {
                start = indexA + stringA.size;
                indexB = this.find(stringB, offset: start);
                indexB !? { output = Substring(this.mid(start, indexB - start), start, indexB) };
            };
        ^output;
    }

    lookup { |string, offset = 0, startString = "=\"", endString = "\""|
        ^this.findInBetween(string++startString, endString, offset).string;
    }

    unquote {
        this.replace("\"");
    }

    traktorPath2path {
        ^this.replace("/:","\\");
    }
} 

+ Nil {
    string {
        ^this;
    }
}

+ Array {
    removeNil {
        // rewrite using .selectIndices(function)
        var delete;
        delete = Array.new(this.size);
        this.do({ |item,i| item.isNil.if({ delete.add(i) }) });
        delete.do({ |item,i| this.removeAt(item-i) });
    }

    filterKey { |key, bpm, tolerance|
        var indices;
        indices = this.selectIndices({ |item, i| item.key.modulate(bpm/item.bpm).compatibility(key) <= tolerance });
        ^this.at(indices);
    }

    filterBPM { |lowBound, upBound, multiplier = 1|
        // for multiplier == 1, this just return all tracks within the bounds, for multiplier == 2, we allow for speed which have double, and for bpm 0.5 we allow for half (NOTE on legacy, it used to include multiplier 4)
        var indices;
        indices = this.selectIndices({ |item, i| (item.bpm >= lowBound) && (item.bpm <= upBound) });
        if(multiplier == 0.5){ indices = indices ++ this.selectIndices({ |item, i| (((item.bpm / 2) >= lowBound) && ((item.bpm / 2) <= upBound)) }) };
        if(multiplier == 2){ indices = indices ++ this.selectIndices({ |item, i| (((item.bpm * 2) >= lowBound) && ((item.bpm * 2) <= upBound)) }) };
        //if(multiplier == 4){ indices = indices ++ this.selectIndices({ |item, i| (((item.bpm * 4) >= lowBound) && ((item.bpm * 4) <= upBound)) || (((item.bpm / 4) >= lowBound) && ((item.bpm / 4) <= upBound)) }) };
        ^this.at(indices);
    }

    rand {
        ^this.at(this.size.rand);
    }
}

+ SimpleNumber {
    modSolve { |step, mod, aBound|
        // solve k*step = receiver (mod mod), for smallest k
        // can be used to find the distance of keys along the circle of fifths
        var bound, k = 0, solved = false, multiplier, output;
        bound = aBound ?? step * mod;
        bound = bound.asInteger;
        while{ (k < bound) && (solved == false) }{
            // we solve k*step + multiplier*mod = receiver, whereby multiplier needs to be integer
            multiplier = (this - (k * step)) / mod;
            if(multiplier.isInteger){
                solved = true;
                output = k;
            }{  // try for negative k aswell
                multiplier = (this - (k.neg * step)) / mod;
                if(multiplier.isInteger){
                    solved = true;
                    output = k;
                }{  // otherwise we increment k and continue
                    k = k + 1;
                }   
            }
        }
        ^output;
    }
}

+ Float {
    isInteger { |threshold = 0.001|
        ^((this.round - this).abs < threshold);
    }
}