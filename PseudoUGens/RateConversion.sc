A2Kcentered {
    *kr { |in|
        //^ A2K.kr(in);
        ^ A2K.kr(in + (Slope.ar(in) * 1 / ControlRate.ir / 2));
    }
}

K2Adiscrete {
    *ar { |in|
        ^ Latch.ar(in,T2A.ar(Changed.kr(in)));
    }
}