# Contributing | Firebase Admin Java SDK

Thank you for contributing to the Firebase community!

 - [Have a usage question?](#question)
 - [Think you found a bug?](#issue)
 - [Have a feature request?](#feature)
 - [Want to submit a pull request?](#submit)
 - [Need to get set up locally?](#local-setup)


## <a name="question"></a>Have a usage question?

We get lots of those and we love helping you, but GitHub is not the best place for them. Issues
which just ask about usage will be closed. Here are some resources to get help:

- Go through the [guides](https://firebase.google.com/docs/admin/setup/)
- Read the full [API reference](https://firebase.google.com/docs/reference/admin/java/)

If the official documentation doesn't help, try asking a question on the
[Firebase Google Group](https://groups.google.com/forum/#!forum/firebase-talk/) or one of our
other [official support channels](https://firebase.google.com/support/).

**Please avoid double posting across multiple channels!**


## <a name="issue"></a>Think you found a bug?

Yeah, we're definitely not perfect!

Search through [old issues](https://github.com/firebase/firebase-admin-java/issues) before
submitting a new issue as your question may have already been answered.

If your issue appears to be a bug, and hasn't been reported,
[open a new issue](https://github.com/firebase/firebase-admin-java/issues/new). Please use the
provided bug report template and include a minimal repro.

If you are up to the challenge, [submit a pull request](#submit) with a fix!


## <a name="feature"></a>Have a feature request?

Great, we love hearing how we can improve our products! Share you idea through our
[feature request support channel](https://firebase.google.com/support/contact/bugs-features/).


## <a name="submit"></a>Want to submit a pull request?

Sweet, we'd love to accept your contribution!
[Open a new pull request](https://github.com/firebase/firebase-admin-java/pull/new/master) and fill
out the provided template.

**If you want to implement a new feature, please open an issue with a proposal first so that we can
figure out if the feature makes sense and how it will work.**

Make sure your changes pass our linter and the tests all pass on your local machine.
Most non-trivial changes should include some extra test coverage. If you aren't sure how to add
tests, feel free to submit regardless and ask us for some advice.

Finally, you will need to sign our
[Contributor License Agreement](https://cla.developers.google.com/about/google-individual)
before we can accept your pull request.


## <a name="local-setup"></a>Need to get set up locally?

### Initial Setup

Install Java 7 or higher. You can also use Java 8, but please note that the Firebase Admin SDK must
maintain full Java 7 compatibility. Therefore make sure that you do not use any Java 8 features
(e.g. lambdas) when writing code for the Admin Java SDK.

We use [Apache Maven](http://maven.apache.org/) for building, testing and releasing the Admin Java
SDK code. Follow the [installation guide](http://maven.apache.org/install.html), and install Maven
3.3 or higher.

### Running Linters

[Maven Checkstyle plugin](https://maven.apache.org/plugins/maven-checkstyle-plugin/) is configured
to run everytime the build is invoked. This plugin verifies source code format, and enforces a
number of other Java programming best practices. Any style violations will cause the build to break.

Configuration for the Checkstyle plugin can be found in the `checkstyle.xml` file at the root of the
repository. To execute only the linter without rest of the build pipeline, execute the following
command:

```
mvn validate
```

If you are using Eclipse for development, you can install the
[Eclipse Checkstyle plugin](http://eclipse-cs.sourceforge.net/#!/), and import the `checkstyle.xml`
file into the IDE. This enables you to have the linter constantly checking your code as you develop.
A similar [plugin](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea) is available for
IntelliJ IDEA as well.

### Unit Testing

Tests are implemented using the [Junit4](http://junit.org/junit4/) framework, and are housed under
the `src/test` subdirectory. They get executed as part of the build, and test failures will cause
the build to break. To run the unit tests without the rest of the build pipeline, execute the
following command:

```
mvn test
```

### Integration Testing

Integration tests are also written using Junit4. They coexist with the unit tests in the `src/test`
subdirectory. Integration tests follow the naming convention `*IT.java` (e.g. `DataTestIT.java`),
which enables the Maven Surefire and Failsafe plugins to differentiate between the two types of
tests. Integration tests are executed against a real life Firebase project, and therefore
requires an Internet connection. Create a new project in the
[Firebase console](https://console.firebase.google.com/) if you do not already have one. Use a 
separate, dedicated project for integration tests since the test suite makes a large number of
writes to the Firebase realtime database. Download the service account private key from the 
"Settings" page of the project. Now run the following command to invoke the integration test suite:

```
mvn verify -Dfirebase.it.certificate=path/to/your/serviceAccount.json
```

Make sure to specify the correct path to your downloaded service account key file as the
`firebase.it.certificate` system property. 

The above command invokes both unit and integration test suites. To execute only the integration
tests, run the command as follows:

```
mvn verify -Dfirebase.it.certificate=path/to/your/serviceAccount.json -Dskip.surefire.tests=true
```



