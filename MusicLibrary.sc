
MusicLibrary {
    var <>tracks, <>playlists;

    *newFromTraktor { |path|
        ^super.new.initFromTraktor(path);
    }

    initFromTraktor { |path|
        var collectionText;
        var tracksText, numberTracks, previousIndex = 0;
        var playlistsText, playlistSubstring;
        collectionText = File.readAllString(path);
        //load tracks
        tracksText = collectionText.findInBetween("<COLLECTION", "</COLLECTION>").string;
        numberTracks = collectionText.lookup("COLLECTION ENTRIES").asInteger;
        tracks = Array.newClear(numberTracks);
        for(0,(numberTracks-1)){ |i|
            var substring, track;
            substring = tracksText.findInBetween("<ENTRY", "</ENTRY>", previousIndex);
            previousIndex = substring.endIndex;
            try{
                track = TrackDescription.newFromTraktor(substring.string);
                tracks.put(i,track);
            }{i.postln; substring.string.postln};
        };
        tracks.removeNil;

        Library.put(\musicLibrary,this);

        //load playlists
        playlistsText = collectionText.findInBetween("<PLAYLISTS>", "</PLAYLISTS>").string;
        playlists = Array.new(200);
        previousIndex = 0;
        while{
            playlistSubstring = playlistsText.findInBetween("<NODE TYPE=\"PLAYLIST\"","</NODE>",previousIndex);
            playlistSubstring.isNil.not;
        }{
            var substring, playlist;
            previousIndex = playlistSubstring.endIndex;
            playlist = Playlist.newFromTraktor(playlistSubstring.string, this);
            playlist.isNil.not.if({playlists.add(playlist)});
        }
        ^this;
    }

    asArray {
        ^tracks;
    }

    findTitle { |string|
        // rewrite using .selectIndices(function)
        var result = Array.new(this.tracks.size);
        tracks.do({ |item, i| if(item.title.find(string).isNil.not){ result.add(item) } });
        ^result;
    }

    findPath { |path|
        ^tracks[this.findPathIndices(path)];
    }

    findPathIndices { |path|
        ^tracks.selectIndices({ |item, i| item.path.find(path).isNil.not });
    }
}

Playlist {
    var <>name, <>tracksIndex;

    *newFromTraktor { |playlistString|
        ^super.new.initFromTraktor(playlistString);
    }

    initFromTraktor { |playlistString|
        var playlistLength, previousIndex = 0, output;
        name = playlistString.lookup("NAME");
        playlistLength = playlistString.lookup("PLAYLIST ENTRIES").asInteger;
        if(playlistLength == 0){
            output = nil;
        }{
            tracksIndex = Array.newClear(playlistLength);
            for(0,(playlistLength-1)){ |i|
                var substring, trackIndex, path, indices;
                substring = playlistString.findInBetween("<ENTRY", "</ENTRY>", previousIndex);
                previousIndex = substring.endIndex;
                path = substring.string.lookup("KEY").traktorPath2path;
                indices = Library.at(\musicLibrary).findPathIndices(path);
                trackIndex = indices[0];
                tracksIndex.put(i, trackIndex);
            };
            output = this;
        };
        ^output;
    }

    randomTrack {
        ^Library.at(\musicLibrary).tracks[tracksIndex[tracksIndex.size.rand]];
    }
}

TrackDescription : SoundFile {
    //make a child from SoundFile
    var <title, <artist, <key, <bpm, <gridOffset;

    *newFromTraktor { |string|
        ^super.new.fromTraktor(string);
    }

    fromTraktor { |string|
        path = (string.lookup("VOLUME")++string.lookup("LOCATION DIR")++string.lookup("FILE")).traktorPath2path;
        title = string.lookup("TITLE");
        try{ artist = string.lookup("ARTIST") }{ artist = "EMPTY" };
        key = string.lookup("MUSICAL_KEY VALUE");
        bpm = string.lookup("TEMPO BPM").asFloat;
        gridOffset = string.lookup("START").asFloat/1000;
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
        try{ // this accounts for the case when there is not such a string
            indexA = this.find(stringA, offset: offset);
            start = indexA + stringA.size;
            indexB = this.find(stringB, offset: start);
            output = Substring(this.mid(start, indexB - start), start, indexB);
        }{
            output = nil;
        }
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

+ Array {
    removeNil {
        // rewrite using .selectIndices(function)
        var delete;
        delete = Array.new(this.size);
        this.do({ |item,i| item.isNil.if({ delete.add(i) }) });
        delete.do({ |item,i| this.removeAt(item-i) });
    }
}