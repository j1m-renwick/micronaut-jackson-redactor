### Todo List
- Check / refine dependencies list
- Add additional unit tests
- Consider adding a non-bean class holding a singleton ObjectMapper instance, with a static `redact(..)` method that uses it
- Look at removing Reflections dependency and put metaclass data in separate file(s) instead
- Add warning about need for @Singleton / @Introspected to trigger the project correctly
  - consider adding @Introspected to the @Redactable annotation


### dev notes

To run:

`./gradlew clean build`

then to publish locally:

`./gradlew publishToMavenLocal`
