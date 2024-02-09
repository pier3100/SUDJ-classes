+ SimpleNumber {
    roundFractional { |divisor|
    // rounds the receiver to the nearest multiple of 1/divisor
    ^(this * divisor).round / divisor;
    }
}

+ Array {
    setToZero {
        this.do( {|item| item = 0 }); // this way we do not overwrite the play object itself; so all references remain intact, but we do overwrite all content
    }
}