Log {
    // allows to easily write a suitable logging file
    var <file;

    *new { |folderPath|
        ^super.new.init(folderPath);
    }

    init { |folderPath_|
        file = File.new(folderPath_++"\\log_"++Date.getDate.stamp++".txt", "w");
        Library.put(\log, this);
    }

    write { |string|
        file.write(string);
    }

    close {
        file.close;
    }
}

+ String {
    log { |object|
        var log, logString;
        logString = Date.getDate.stamp++" \t"++object.class.asString++": "++this++"\n";
        logString.postf;
        log = Library.at(\log);
        log !? { log.write(logString) };
    }
}