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
        file.write(Date.getDate.stamp++" \t"++string++"\n");
    }

    close {
        file.close;
    }
}

+ String {
    log { |object|
        var log;
        log = Library.at(\log);
        log !? { if(object.isNil){ log.write(this) }{ log.write(object.class.asString++": "++this)} };
    }
}