### Todo List
- Check / refine dependencies list
- Add additional unit tests
- Check persistence integrations to make sure objectmapper does not get used for serialisation 
  - consider wrapping a seperate ObjectMapper instance instead if needed
- Look at removing Reflections dependency and put metaclass data in separate file(s) instead

### dev notes

To run:

`./gradlew clean build`

then to publish locally:

`./gradlew publishToMavenLocal`
