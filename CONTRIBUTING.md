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
- Read the full [API reference](https://firebase.google.com/docs/reference/admin#java)

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
[Contributor License Agreement](https://cla.developers.google.com/about/google-individual),
and go through our code review process before we can accept your pull request.

### Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution.
This simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

### Code reviews

All submissions, including submissions by project members, require review. We
use GitHub pull requests for this purpose. Consult
[GitHub Help](https://help.github.com/articles/about-pull-requests/) for more
information on using pull requests.


## <a name="local-setup"></a>Need to get set up locally?

### Initial Setup

Install Java 8 or higher.

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
tests.

Integration tests are executed against a real life Firebase project. If you do not already
have one suitable for running the tests against, you can create a new project in the
[Firebase Console](https://console.firebase.google.com) following the setup guide below.
If you already have a Firebase project, you'll need to obtain credentials to communicate and
authorize access to your Firebase project:

1. Service account certificate: This allows access to your Firebase project through a service account
which is required for all integration tests. This can be downloaded as a JSON file from the 
**Settings > Service Accounts** tab of the Firebase console when you click the
**Generate new private key** button. Copy the file into the repo so it's available at
`integration_cert.json`.


2. Web API key: This allows for Auth sign-in needed for some Authentication and Tenant Management
integration tests. This is displayed in the **Settings > General** tab of the Firebase console
after enabling Authentication as described in the steps below. Copy it and save to a new text
file at `integration_apikey.txt`.


Set up your Firebase project as follows:


1. Enable Authentication:
   1. Go to the Firebase Console, and select **Authentication** from the **Build** menu.
   2. Click on **Get Started**.
   3. Select **Sign-in method > Add new provider > Email/Password** then enable both the
   **Email/Password** and **Email link (passwordless sign-in)** options.


2. Enable Firestore:
   1. Go to the Firebase Console, and select **Firestore Database** from the **Build** menu.
   2. Click on the **Create database** button. You can choose to set up Firestore either in
   the production mode or in the test mode.


3. Enable Realtime Database:
   1. Go to the Firebase Console, and select **Realtime Database** from the **Build** menu.
   2. Click on the **Create Database** button. You can choose to set up the Realtime Database
   either in the locked mode or in the test mode.

   > **Note:** Integration tests are not run against the default Realtime Database reference and are
   instead run against a database created at `https://{PROJECT_ID}.firebaseio.com`.
   This second Realtime Database reference is created in the following steps.

   3. In the **Data** tab click on the kebab menu (3 dots) and select **Create Database**.
   4. Enter your Project ID (Found in the **General** tab in **Account Settings**) as the
   **Realtime Database reference**. Again, you can choose to set up the Realtime Database
   either in the locked mode or in the test mode.


4. Enable Storage:
   1. Go to the Firebase Console, and select **Storage** from the **Build** menu.
   2. Click on the **Get started** button. You can choose to set up Cloud Storage
   either in the production mode or in the test mode.


5. Enable the IAM API:
   1. Go to the [Google Cloud console](https://console.cloud.google.com)
   and make sure your Firebase project is selected.
   2. Select **APIs & Services** from the main menu, and click the
   **ENABLE APIS AND SERVICES** button.
   3. Search for and enable **Identity and Access Management (IAM) API** by Google Enterprise API.


6. Enable Tenant Management:
   1. Go to
   [Google Cloud console | Identity Platform](https://console.cloud.google.com/customer-identity/)
   and if it is not already enabled, click **Enable**.
   2. Then
   [enable multi-tenancy](https://cloud.google.com/identity-platform/docs/multi-tenancy-quickstart#enabling_multi-tenancy)
   for your project.


7. Ensure your service account has the **Firebase Authentication Admin** role. This is required
to ensure that exported user records contain the password hashes of the user accounts:
   1. Go to [Google Cloud console | IAM & admin](https://console.cloud.google.com/iam-admin).
   2. Find your service account in the list. If not added click the **pencil** icon to edit its
   permissions.
   3. Click **ADD ANOTHER ROLE** and choose **Firebase Authentication Admin**.
   4. Click **SAVE**.


Now run the following command to invoke the integration test suite:

```
mvn verify
```

The above command invokes both unit and integration test suites. To execute only the integration
tests, specify the `-DskipUTs` flag.

### Generating API Docs

Invoke the [Maven Javadoc plugin](https://maven.apache.org/plugins/maven-javadoc-plugin/) as
follows to generate API docs for all packages in the codebase:

```
mvn javadoc:javadoc
```

This will generate the API docs, and place them in the `target/site/apidocs` directory.

To generate API docs for only the public APIs (i.e. ones that are not marked with `@hide` tags),
you need to trigger the `devsite-apidocs` Maven profile. This profile uses Maven Javadoc plugin
with [Doclava](https://code.google.com/archive/p/doclava/), which honors the `@hide` tags in
source code. Dovlava also accepts a set of [Clearsilver](http://www.clearsilver.net/) templates as
a parameter. You can trigger this Maven profile by running the following command:

```
mvn site -Ddevsite.template=path/to/templates/directory/
```

This command will place the generated API docs in the `target/apidocs` directory.

