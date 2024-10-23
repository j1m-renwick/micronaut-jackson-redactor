# Micronaut Jackson Redactor

Redacts Plain Old Java and Groovy Objects (POJOs and POGOs) when using Jackson's ObjectMapper.


> **IMPORTANT:**
> You are solely responsible for using and testing that redaction works as expected using this library.


### Usage

Import the dependencies into your gradle project (TBC):

```

    annotationProcessor "com.wrennix:micronaut-jackson-redactor:0.1"
    implementation 'com.wrennix:micronaut-jackson-redactor:0.1'

    annotationProcessor("io.micronaut.sourcegen:micronaut-sourcegen-generator-java")
    compileOnly("io.micronaut.sourcegen:micronaut-sourcegen-generator-java")
    compileOnly "io.micronaut.sourcegen:micronaut-sourcegen-model"
    compileOnly("io.micronaut.sourcegen:micronaut-sourcegen-generator")
    
    compileOnly('org.reflections:reflections:0.10.2')
```

Annotate your POJO/POJO files:

```
    package com.example;
    
    import com.annotation.annotations.Redact;
    import com.annotation.annotations.Redactable;
    
    @Redactable
    public class MyPojo {
    
        public String unredactedField;
    
        @Redact
        public String redactedField;

    }
```

Create an implementation of RedactorConfiguration:

```
    package com.example;
    
    import com.annotation.interfaces.RedactorDecisioner;
    import io.micronaut.context.annotation.Value;
    import jakarta.inject.Singleton;
    
    @Singleton
    public class MyRedactorConfiguration implements RedactorConfiguration {
    
        @Value("${redaction.enabled}")
        boolean redactionEnabled;
        
        /**
         *
         * @return true if redaction should be applied at application startup.
         */
        boolean shouldRedact() {
            return redactionEnabled;
        }
    
        /**
         * A package name that encompasses all the POGOs that should be targeted 
           for redaction processing.
         *
         * @return your project package path or a relevant subpath.
         */
        String rootPackage() {
            return "com.example.myproject.mypogos"
        }
    }
```

Test the redaction occurs:

```
    MyPojo pojo = new MyPojo();
    pojo.unredactedField = "normalField";
    pojo.redactedField = "redactedField";
    
    System.out.println(objectMapper.writeValueAsString(pojo));
    // {"unredactedField":"unredactedField","redactedField":"XXXX"}
```

## Configuration Options

### Redactor Types

The package currently offers three redaction serializer options:

- EXES_REDACTION - replaces with the constant "XXXX"
- CARET_TEXT_REDACTION - replaces with the constant "\<REDACTED>"
- ALPHANUMERIC_REDACTION - replaces alphanumeric characters with either "X" or "9"

You can specify one of these in your `@Redact` field annotations:

```
    ...

    @Redact(redactor = RedactorType.ALPHANUMERIC_REDACTION)
    String myfield;
    
    ...
```

> **NOTE:**
> Currently the default redactor is EXES_REDACTION.
>
> To specify your own redaction serializer, see the Custom Mixins section below.


### Custom Mixins

This package makes use of [Jackson Mixins](https://www.tutorialspoint.com/jackson_annotations/jackson_annotations_mixin.htm)
to serialise the redacted data. If you want to use your own custom mixin (for reasons such as creating and
using your own custom Serializers for redaction), you can specify this in the `@Redactable` annotation:

```
    @Redactable(mixin = "com.example.mixins.MyCustomMixin")
    public class PojoTwo {
    
        public String fieldOne;
    
        public String fieldTwo;
    
    }
```

> **NOTE:**
> When a custom mixin is specified, compilation will fail if any fields in the same POJO are annotated with `@Redact`.
> This is to avoid users mistakenly considering these annotations as functional on the POJO.

### 3rd party POJOs

You may need to redact POJOs that exist in other packages that cannot be modified. You can do this by
copying the 3rd party POJO into your project, and referencing the original POJO via the `@Redactable` annotation:
```
    @Redactable(proxyFor = "com.legacy.package.SomeThirdPartyPojo")
    public class ThirdPartyPojoCopy {
    
        public String fieldOne;
    
        @Redact
        public String fieldTwo;
    
    }
```

> **NOTE:**
> You should not use (or have to use) this new POJO class in your project - just use the 3rd party POJO
> as normal.
>
> Nevertheless, the same redaction will also be applied to this new POJO class,
> just in case.

