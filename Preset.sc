/*
DESIGN
- the preset system should be independent from the physical interfaces
- it should allow for almost all object
- we should be able to link it back to the objects, also if this object changes slightly
    -> assign a key to each object
- for each object we should be able to specify what is included in the preset and what not

maybe add a method to the object class
.registerForPreset(key), which upon calling registers the object in the preset system, which can be a sub library in the Library
    -> we could also use equality, and make sure the equality and hash is defined as we like, but this is not so robust and flexibel, the only drawback of the keySystem is the burden on the syntax, but we can just bundle this on one .scd file
.loadPreset, called from the PresetManager, load and store should correspond to each other; the basic implementation which can and should be overridden, is to store the entire object instance, and upon loading do nothing
.storePreset, called from the PresetManager
    -> this is just a placeholder, perhaps later we want to allow for more specific presets, for now this returns the entire object, which is to be stored

Define a class PresetManager 
which has methods for loading and storing presets, and keeps track of all objects registered when calling preset


*/