package com.geekbeast.rhizome.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;

public interface Configuration extends Serializable {
    @JsonIgnore
    ConfigurationKey getKey();
}
