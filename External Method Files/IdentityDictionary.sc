+ IdentityDictionary {
    selectKeys { |func|
        var array, indices, selectecValues, selectedKeys;
        selectedKeys = Array.new(this.size);
        array = this.asArray;
        indices = array.selectIndices(func);
        selectecValues = array[indices];
        selectecValues.do({ |item| var key; (key = this.findKeyForValue(item)) !?({ selectedKeys.add(key) }) });
        ^selectedKeys;
    }
}