+ PathName {
    asPathName {
        ^this;
    }
}

+ String {
    formatPlain {
        ^this.replace("&amp;","&");
    }
}

+ Nil {
    formatPlain {
        ^this;
    }
}