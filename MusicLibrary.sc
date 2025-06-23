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

    *loadFromTraktor{ |libraryPath, musicLibraryCustomPartPath, traktorLibraryPath, forceLoad = false|
        // load the archived library and update it from the Traktor collection if needed, if no archive exists, create new library from scratch from the Traktor collection
        var instance, tracksOld;
        if(File.exists(libraryPath) && forceLoad.not){
            // reusing existing library is broken; but it is also redundant is reloading from scratch is considerably faster
            instance = this.load(libraryPath);
            // apparently there is a bug in supercollider in loading the track dictionary; which we can circumvent if we overwrite it
            //tracksOld = instance.tracks;
            //instance.tracks = Library.new(tracksOld.size);
            //tracksOld.keysValuesDo({|key, value| instance.tracks.put(key,value)});

            if(File.mtime(traktorLibraryPath) > File.mtime(libraryPath)){ // if traktor has recently updated the collection, we need to update our collection as well
                var collectionText;
                "Updating Traktor library".log(this);
                collectionText = File.readAllString(traktorLibraryPath);
                instance.updateTracksFromTraktor(collectionText, Date.rawSeconds(File.mtime(libraryPath)).asSortableString);
                Library.put(\musicLibrary,instance); // should happen before loading the playlists, because the Playlist.new method lookup the tracks in the musicLibrary
                instance.loadAllPlaylists(collectionText); // overwrite all playlists
            }{
                "Reusing existing music library, no need to update".log(this);
                Library.put(\musicLibrary,instance); // should happen before loading the playlists, because the Playlist.new method lookup the tracks in the musicLibrary
            }
        }{
            "Loading library from Traktor from scratch".log(this);
            instance = this.newFromTraktor(traktorLibraryPath);
        };
        instance.playlists.leafDo({ |path, item| if(item.class == Smartlist || item.class == PseudoPlaylist){ item.selectTracks }} ); // we make sure we have up to date tracklist for automatically generated playlists
        instance.playlists.put("$ROOT".asSymbol, \Custom, musicLibraryCustomPartPath.load);
        instance.barcodeDictionary_;
        ^instance;
    }

    *newFromTraktor { |path|
        var instance, collectionText;
        collectionText = File.readAllString(path);
        instance = super.new;
        instance.loadTracksFromTraktor(collectionText);
        Library.put(\musicLibrary,instance); // should happen before loading the playlists, because the Playlist.new method lookup the tracks in the musicLibrary
        instance.loadAllPlaylists(collectionText);
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
            var substring, path, string, track, dateModifiedTrack;
            substring = tracksText.findInBetween("<ENTRY", "</ENTRY>", previousIndex);
            previousIndex = substring.endIndex;
            string = substring.string;       
            dateModifiedTrack = string.lookup("MODIFIED_DATE").replace("\/");
            if(dateModifiedTrack > dateModifiedLibrary){ // we only need to load the track if it is updated recently; we compare the strings, which are suited for this purpose
                path = (string.lookup("VOLUME")++string.lookup("LOCATION DIR")++string.lookup("FILE")).formatPlain.traktorPath2path;
                track = tracks.at(path.asSymbol); // retrieve track in library by looking it up by id
                if(track.isNil){ 
                    track = TrackDescription.newFromTraktor(string);
                    tracks.put(track.path.asSymbol, track);
                     "track % appended to tracklist: \t %".format(i, substring.string).log(this);
                    }{
                    if(track.usable.not){ "track % not usable but still in tracklist: \t %".format(i, substring.string).log(this) }; // this is a bit ugly because if the new Track information 
                }
            }
        };
    }

    loadTracksFromTraktor { |collectionText|
        //load tracks
        var tracksText, numberTracks, previousIndex = 0;
        tracksText = collectionText.findInBetween("<COLLECTION", "</COLLECTION>").string;
        numberTracks = collectionText.lookup("COLLECTION ENTRIES").asInteger;
        tracks = Dictionary.new(numberTracks);
        for(0, (numberTracks - 1)){ |i|
            var substring, track;
            substring = tracksText.findInBetween("<ENTRY", "</ENTRY>", previousIndex);
            previousIndex = substring.endIndex;
            track = TrackDescription.newFromTraktor(substring.string);
            if(track.usable){ tracks.put(track.path.asSymbol, track) }{ "track % not usable: \t %".format(i, substring.string).log(this) };
        };
        tracks.removeNil;
        ^tracks;
    }

    loadAllPlaylists { |collectionText|
        var customPlaylistFolder, specialPlaylistFolder;
        this.loadPlaylistsFromTraktor(collectionText); 
        customPlaylistFolder = IdentityDictionary.new;
        specialPlaylistFolder = IdentityDictionary.new;
        playlists.put("$ROOT".asSymbol, \Custom, customPlaylistFolder);
        playlists.put("$ROOT".asSymbol, \Special, specialPlaylistFolder);
        //this.barcodeDictionary_; // we do it later after having added our custom playlists
    }

    loadPlaylistsFromTraktor { |collectionText|
        //load playlists
        var playlistsText, playlistSubstring, playlistFolderSubstring, previousIndexPlaylistFolder = 0, previousIndexPlaylist;
        playlistsText = collectionText.findInBetween("<PLAYLISTS>", "</PLAYLISTS>").string;
        playlists = MultiLevelIdentityDictionary.new;

        // we start by opening the root folder and then recursively iterate
        this.loadPlaylistFolder(playlistsText, 0, playlists);
        ^playlists;
    }

    loadPlaylistFolder { |playlistsText, startIndex, parentDictionary|
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
                previousIndex = this.loadPlaylistFolder(playlistsText, nodeTypeSubstring.endIndex, thisFolder);
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
        playlists.leafDo({ |path, item| 
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
                string = playlist.barcodeId.asString.barcodeId2EAN13(1) ++ "; " ++ folderNames[i].asString ++ " " ++ playlist.name ++ "; \n"; // we need to convert the barcode ID to an actual barcode
                csvFile.write(string);
            });
        };
        csvFile.close;
    }

    findTrackByTitle { |string|
        ^tracks.select({ |item| item.title.find(string).isNil.not});
    }

    findPath { |path|
        ^tracks.select({ |item| item.path == path }).[0];
    }

    findPathKeys { |path|
        ^tracks.selectKeys({ |item| item.path == path });
    }

    findPlaylistByTitle { |string|
    //TODO remove case sensitivity
        playlists.leafDo({|path, item| if(item.name.find(string).isNil.not){ "%\t".postf( item.name); path.postcs}});
    }

    renamePlaylist { |path, newName|
        var playlist;
        playlist = playlists.at(*path);
        playlists.removeAt(*path);
        playlist.name = newName;
        playlist.addToLibrary(*path);
    }

    store { |path|
        this.writeArchive(path);
    }

    storeCustom { |path|
        this.playlists.at("$ROOT".asSymbol, \Custom).writeArchive(path);
    }
}

AbstractPlaylist {
    var <>name, <>uuId, <>barcodeId, <>trackKeyArray;

    *new {
        ^super.new;
    }

    setName { |name_, uuId_|
        name = name_;
        uuId = uuId_ ? ("sudjsudj" ++ name ++ "sudjsudj").asHexAscii; // all our own custom playlists, get this prefix and postfix
        barcodeId = this.uuId2barcodeId(uuId);
    }

    uuId2barcodeId { |uuIdString|
        // the uuID is expected to be 32 characters long, the barcodeID can only be 9 digits long, furthermore uuID is hexidecimal, while barcodeID should be decimal (since we can only communicate a decimal number via hidapi)
        // we take a selection of digits
        ^(uuIdString.digit*(2**Array.series(uuIdString.size,0,1))).sum.asString.copyRange(0,8).asSymbol;
    }

    asTrackArray {
        ^Library.at(\musicLibrary).tracks.atAll(trackKeyArray);
    }

    randomTrack { 
        ^Library.at(\musicLibrary).tracks[trackKeyArray.rand];
    }

    addTrack { |track|
        trackKeyArray = trackKeyArray ++ track.path.asSymbol;
    }

    installCustom {
        // convenience method
         ^Library.at(\musicLibrary).playlists.put("$ROOT".asSymbol, \Custom, this.name.asSymbol, this);
    }

    post {
        "Playlist %\n".postf(name);
        "Index\tArtist\tTitle\n".postf;
        for(0, trackKeyArray.size - 1){ |i|
            var track = Library.at(\musicLibrary).tracks.at(trackKeyArray[i]);
            "%\t%\t%\n".postf(i, track.artist, track.title);
        };
    }

    deleteTrack { |index|
        trackKeyArray.removeAt(index);
    }
}

Playlist : AbstractPlaylist {
    *new { |name, trackKeyArray, uuId|
        ^super.new.setName(name, uuId).init(trackKeyArray);
    }
    
    *newFromTraktor { |playlistString|
        ^super.new.initFromTraktor(playlistString);
    }

    init { |trackKeyArray_|
        trackKeyArray = trackKeyArray_ ? Array.newClear;
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
            trackKeyArray = Array.newClear(playlistLength);
            for(0,(playlistLength-1)){ |i|
                var substring, path;
                substring = playlistString.findInBetween("<ENTRY", "</ENTRY>", previousIndex);
                previousIndex = substring.endIndex;
                path = substring.string.lookup("KEY").formatPlain.traktorPath2path;
                if(Library.at(\musicLibrary).tracks.at(path.asSymbol).isNil){ // check if the path is a key in the tracklist
                    "track missing in %: \t %".format(name, path).log(this)
                }{
                    trackKeyArray.put(i, path.asSymbol);
                }
            };
            trackKeyArray.removeNil;
            output = this;
        };
        ^output;
    }
}

PseudoPlaylist : AbstractPlaylist {
    var <>folderPathArray;

    *new { |name_, folderPathArray_, uuId_| 
        ^super.new.setName(name_, uuId_).init(folderPathArray_);
    }

    init { |folderPathArray_|
        folderPathArray = folderPathArray_;
        this.selectTracks;
    }

    selectTracks {
        var trackList = [];
        Library.at(\musicLibrary).playlists.at(*folderPathArray).do({ |containedPlaylist|
            trackList = trackList ++ containedPlaylist.trackKeyArray;
        });
        trackKeyArray = trackList.asSet.asArray;
    }

    addToLibrary {
        Library.at(\musicLibrary).playlists.put("$ROOT".asSymbol, \Special, name.asSymbol, this);
    }
}

Smartlist : AbstractPlaylist {
    // a playlist which is formed by selecting all tracks which abide the ruleFunction; it is only updated upon request; so you can add tracks manually afterwards
    var <>ruleFunction;

    *new { |name_, ruleFunction_, uuId_| 
        ^super.new.setName(name_, uuId_).init(ruleFunction_);
    }

    init { |ruleFunction_|
        ruleFunction = ruleFunction_;
        this.selectTracks;
    }

    selectTracks { 
        trackKeyArray = Library.at(\musicLibrary).tracks.selectKeys(ruleFunction);
    }

    addToLibrary {
        Library.at(\musicLibrary).playlists.put("$ROOT".asSymbol, \Special, name.asSymbol, this);
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
        if(keyNumber>=12){ scale = Scale.minor; rootNote = keyNumber - 12 }{ scale = Scale.major; rootNote = keyNumber };
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
                shiftedNote = (rootNote + 3).mod(12); // for the minor scale we add + 2, now we can compare the keys as if they were both major, according to the circle of fifths
                score = this.compatibilityCircleOfFifthsMajorKeys(shiftedNote,key.rootNote);
            }{ //in this case necessarily the other key is minor
                shiftedNote = (key.rootNote + 3).mod(12);
                score = this.compatibilityCircleOfFifthsMajorKeys(rootNote,shiftedNote);
            }
        }
        ^score;        
    }

    compatibilityCircleOfFifthsMajorKeys { |rootNoteA, rootNoteB|
        // only makes sense if both keys are major
        var noteDif, noteDifRound, fifthScore, penalty, finalScore;
        noteDif = rootNoteA.moddif(rootNoteB, 12);
        noteDifRound = noteDif.round;
        fifthScore = this.fifthDistance(noteDif); // it outputs the distance along the circle of fifths (a fifth is 7 semitones apart)
        //penalty = 2 * (noteDif.floor - noteDif).abs * (noteDif.ceil - noteDif).abs * 5; // we interpolate the penalty which you get wrt bigger key and smaller key differnce; a penalty from deviating of being perfect; where the multiplier of 5 is chosen such that for notes next to each other you get a score of 5, which makes sense because in the circle of fiths that's also there distance
        if(noteDif >= noteDifRound){ penalty = noteDif.frac }{ penalty = 1 - noteDif.frac }; // we asign as penalty the linear difference between the rootNote pitches; since we can not really interpret the meaning if the penalty, it doesnt really matter much how we calculate it; it should just resemble how much we deviate from perfect fifths
        ^finalScore = fifthScore + penalty;
    }

    fifthDistance { |semiToneDistance| 
        ^semiToneDistance.round.modSolve(7,12).abs;
    }

    fifthScore { |semiToneDistance| 
        var semiToneDistanceAugmented;
        if(scale==Scale.minor){ semiToneDistanceAugmented = (semiToneDistance + 3).mod(12) }{ semiToneDistanceAugmented = semiToneDistance };
        ^1 + semiToneDistanceAugmented.round.modSolveP(7,12);
    }
}

TrackDescription : SoundFile {
    //make a child from SoundFile
    var <usable = true, <title, <artist, <key, <bpm, <gridOffset, <>userInducedGridOffset, <id, <preceivedDb, <energy, <dateAdded;

    *newDummy { |bpm, keyNumber|
        ^super.openRead(Platform.resourceDir +/+ "sounds/a11wlk01.wav").init(bpm, keyNumber);
    }

    *newFromTraktor { |string|
        var instance; 
        instance = super.new.fromTraktor(string);
        //instance.readMetaData;
        ^instance;
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
        userInducedGridOffset = gridOffset;
        id = string.lookup("AUDIO_ID");
        preceivedDb = string.lookup("PERCEIVED_DB");
        if(preceivedDb.isNil){ usable = false }{ preceivedDb = preceivedDb.asFloat };
        energy = string.lookup("LABEL");
        dateAdded = string.lookup("IMPORT_DATE").replace("\/");
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

    readMetaData {
        this.energyFromMetaData;
    }

    energyFromMetaData { 
        var command;
        if(path.extension == "ogg"){
            command = "ffprobe -v quiet -print_format json -show_entries stream_tags=ORGANIZATION \"" ++ path + "\"";
            energy = command.unixCmdGetStdOut.findInBetween("ORGANIZATION\": \"", "\"").string;
        };
        if(path.extension == "mp3"){
            command = "ffprobe -v quiet -print_format json -show_entries format_tags=publisher \"" ++ path + "\"";
            energy = command.unixCmdGetStdOut.findInBetween("publisher\": \"", "\"").string;
        };
        ^energy;
    }

    info {
        "\tTitle:\t%\n".postf( this.title );
        "\tArtist:\t%\n".postf( this.artist );
        "\tBPM:\t%\n".postf( this.bpm );
        "\tKey:\t% % \tFifthScore: \t%\n".postf( this.key.rootNote, this.key.scale.name, this.key.fifthScore(this.key.rootNote) );
        "\tLength:\t%\n".postf(this.duration);
        "\tPerceivedDb:\t%\n".postf(this.preceivedDb);
        "\n".postf();
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

    removeNotUsable {
        var delete;
        delete = this.selectIndices({ |item, i| item.usable.not });
        delete.do({ |item,i| this.removeAt(item-i) });
    }

    filterKey { |key, bpm, distanceLow, distanceHigh, minorMajor|
        // filters the track array with respect to a reference key, whereby both the reference track and toBeFiltered tracks are repitched to the same bpm; only output tracks whose keyDistance is in between the distance tresholds
        var indices, minorMajorFiltered;
        minorMajorFiltered = this;
        // filter based on minorMajor
        if(minorMajor == "major"){
            indices = this.selectIndices({ |item, i| item.key.scale == Scale.major });
            minorMajorFiltered = this.at(indices);
        };
        if(minorMajor == "minor"){
            indices = this.selectIndices({ |item, i| item.key.scale == Scale.minor });
            minorMajorFiltered = this.at(indices);
        };
        // filter based on circle of fifth distance
        indices = minorMajorFiltered.selectIndices({ |item, i| 
            var keyDistance;
            keyDistance = item.key.modulate(bpm/item.bpm).compatibility(key);
            (keyDistance >= distanceLow) && (keyDistance <= distanceHigh) });
        ^minorMajorFiltered.at(indices);
    }

    filterBPM { |lowBound, upBound, multiplier = 1|
        // for multiplier == 1, this just return all tracks within the bounds, for multiplier == 2, we allow for speed which have double, and for bpm 0.5 we allow for half (NOTE on legacy, it used to include multiplier 4)
        var indices;
        indices = this.selectIndices({ |item, i| (item.bpm >= lowBound) && (item.bpm <= upBound) });
        if(multiplier == 0.5){ indices = indices ++ this.selectIndices({ |item, i| (((item.bpm * 2) >= lowBound) && ((item.bpm * 2) <= upBound)) }) };
        if(multiplier == 2){ indices = indices ++ this.selectIndices({ |item, i| (((item.bpm / 2) >= lowBound) && ((item.bpm / 2) <= upBound)) }) };
        //if(multiplier == 4){ indices = indices ++ this.selectIndices({ |item, i| (((item.bpm * 4) >= lowBound) && ((item.bpm * 4) <= upBound)) || (((item.bpm / 4) >= lowBound) && ((item.bpm / 4) <= upBound)) }) };
        ^this.at(indices.asSet.asArray);
    }

    filterEnergy { |energy|
        if(energy.isNil){
            ^this;
        }{
            ^this.select({ |item| item.energy == energy });
        };
    }

    rand {
        ^this.at(this.size.rand);
    }
}

+ Dictionary {
    selectKeys { |function|
        var res = Array.new(this.size);
		this.keysValuesDo {|key, item| if (function.value(item, key)) { res.add(key) } }
		^res;
    }

    removeNil {
        this.keysValuesDo({ |key,item| if(item.isNil){ this.removeAt(key) }});
    }

    removeNotUsable {
        this.keysValuesDo({ |key,item| if(item.usable.not){ this.removeAt(key) }});
    }

    filterKey { |key, bpm, distanceLow, distanceHigh, minorMajor|
        // filters the track array with respect to a reference key, whereby both the reference track and toBeFiltered tracks are repitched to the same bpm; only output tracks whose keyDistance is in between the distance tresholds
        var paths, minorMajorFiltered;
        minorMajorFiltered = this;
        // filter based on minorMajor
        if(minorMajor == "major"){
            paths = this.selectKeys({ |item| item.key.scale == Scale.major });
            minorMajorFiltered = this.atAll(paths);
        };
        if(minorMajor == "minor"){
            paths = this.selectKeys({ |item| item.key.scale == Scale.minor });
            minorMajorFiltered = this.atAll(paths);
        };
        // filter based on circle of fifth distance
        paths = minorMajorFiltered.selectKeys({ |item| 
            var keyDistance;
            keyDistance = item.key.modulate(bpm/item.bpm).compatibility(key);
            (keyDistance >= distanceLow) && (keyDistance <= distanceHigh) });
        ^minorMajorFiltered.atAll(paths);
    }

    filterBPM { |lowBound, upBound, multiplier = 1|
        // for multiplier == 1, this just return all tracks within the bounds, for multiplier == 2, we allow for speed which have double, and for bpm 0.5 we allow for half (NOTE on legacy, it used to include multiplier 4)
        var paths;
        paths = this.selectKeys({ |item| (item.bpm >= lowBound) && (item.bpm <= upBound) });
        if(multiplier == 0.5){ paths = paths ++ this.selectKeys({ |item| (((item.bpm * 2) >= lowBound) && ((item.bpm * 2) <= upBound)) }) };
        if(multiplier == 2){ paths = paths ++ this.selectKeys({ |item| (((item.bpm / 2) >= lowBound) && ((item.bpm / 2) <= upBound)) }) };
        //if(multiplier == 4){ indices = indices ++ this.selectIndices({ |item, i| (((item.bpm * 4) >= lowBound) && ((item.bpm * 4) <= upBound)) || (((item.bpm / 4) >= lowBound) && ((item.bpm / 4) <= upBound)) }) };
        ^this.atAll(paths.asSet.asArray);
    }
}

+ SimpleNumber {
    modSolve { |step, mod, aBound|
        // solve k*step = receiver (mod mod), for k, s.th. abs(k) is smallest
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
                    output = k.neg;
                }{  // otherwise we increment k and continue
                    k = k + 1;
                }   
            }
        }
        ^output;
    }

        modSolveP { |step, mod, aBound|
        // solve k*step = receiver (mod mod), for k, s.th. abs(k) is smallest
        // can be used to find the score of keys along the circle of fifths
        // we only ascend, P for Positive
        var bound, k = 0, solved = false, multiplier, output;
        bound = aBound ?? step * mod;
        bound = bound.asInteger;
        while{ (k < bound) && (solved == false) }{
            // we solve k*step + multiplier*mod = receiver, whereby multiplier needs to be integer
            multiplier = (this - (k * step)) / mod;
            if(multiplier.isInteger){
                solved = true;
                output = k;
            }{ // otherwise we increment k and continue
                    k = k + 1; 
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