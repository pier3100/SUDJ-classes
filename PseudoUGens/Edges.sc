PositiveEdge {
    *kr { |in|
        ^Changed.kr(in) * (in > 0);
    }

    *ar { |in|
        ^Changed.ar(in) * (in > 0);
    }
}

NegativeEdge {
    *kr { |in|
        ^Changed.kr(in) * (in <= 0);
    }

    *ar { |in|
        ^Changed.ar(in) * (in <= 0);
    }
}