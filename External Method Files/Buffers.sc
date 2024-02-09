+ Buffer {
    copyTrimmed { |buf, startFrame, numFrames, threshold = 0.0001, resolution = 0.01|
        // we trim the selected part of this buffer and copy to buf buffer; (with precision of a 0.01 second by default) such that the empty tail is deleted
        var checkFrame, newCheckFrame, stepFrame, recursiveFunc, numFramesTrimmed, chuckSize = 10;
        checkFrame = (startFrame + numFrames - 1).round;
        stepFrame = (Server.default.sampleRate * resolution).round;
        recursiveFunc = { |val| var valAbs, maximum; valAbs = val.abs; maximum = valAbs[valAbs.maxIndex]; newCheckFrame = checkFrame - stepFrame; if((maximum < threshold) && ((newCheckFrame - chuckSize) > 0)){ checkFrame = newCheckFrame; this.getn(checkFrame - chuckSize, chuckSize, recursiveFunc) }{ 
            numFramesTrimmed = checkFrame - startFrame; 
            buf.numFrames_(numFramesTrimmed);
            Server.default.makeBundle(nil, {
                buf.alloc;
                this.copyData(buf, dstStartAt: 0, srcStartAt: startFrame, numSamples: numFramesTrimmed);
                });
            } 
        };
        this.getn(checkFrame - chuckSize, chuckSize, recursiveFunc); // recursively get the index upto which the buffer is non-empty; then in the end copy this part of the buffer to the target buffer buf
    }
}