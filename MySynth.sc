MicroSynthController {
    var <>valueFunction, <>updateFunction, <>yieldFunction, <>dictionary;

    *new {arg function;
        ^super.new.init(function);
    }

    init {arg valueFunction, updateFunction, yieldFunction, dictionary;
        this.valueFunction_(valueFunction);
        dictionary = Dictionary.new(n: 8);
    }

    update {
        dictionary = updateFunction.value(dictionary);
    }

    value {
        valueFunction.value(dictionary);
    }

    yield {
        yieldFunction.value;
    }
}