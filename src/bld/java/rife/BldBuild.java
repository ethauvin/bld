/*
 * Copyright 2001-2023 Geert Bevin (gbevin[remove] at uwyn dot com)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife;

import rife.bld.BuildCommand;
import rife.bld.Project;
import rife.bld.dependencies.VersionNumber;
import rife.bld.extension.Antlr4Operation;
import rife.bld.extension.TestsBadgeOperation;
import rife.bld.extension.ZipOperation;
import rife.bld.operations.*;
import rife.bld.publish.*;
import rife.bld.wrapper.Wrapper;
import rife.tools.DirBuilder;
import rife.tools.FileUtils;
import rife.tools.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.Attributes;
import java.util.regex.Pattern;

import static rife.bld.dependencies.Repository.*;
import static rife.bld.dependencies.Scope.*;
import static rife.bld.operations.JavadocOptions.DocLinkOption.NO_MISSING;
import static rife.bld.operations.TemplateType.*;
import static rife.tools.FileUtils.path;

public class BldBuild extends Project {
    public BldBuild()
    throws Exception {
        pkg = "rife";
        name = "bld";
        mainClass = "rife.bld.Cli";
        version = version(FileUtils.readString(new File(srcMainResourcesDirectory(), "BLD_VERSION")));

        javaRelease = 17;
        downloadSources = true;
        autoDownloadPurge = true;

        repositories = List.of(MAVEN_CENTRAL, RIFE2_RELEASES);
        scope(test)
            .include(dependency("org.slf4j", "slf4j-simple", version(2,0,7)))
            .include(dependency("net.imagej", "ij", version("1.54d")))
            .include(dependency("org.junit.jupiter", "junit-jupiter", version(5,9,3)))
            .include(dependency("org.junit.platform", "junit-platform-console-standalone", version(1,9,3)))
            .include(dependency("com.h2database", "h2", version(2,1,214)))
            .include(dependency("net.sourceforge.htmlunit", "htmlunit", version(2,70,0)))
            .include(dependency("org.postgresql", "postgresql", version(42,6,0)))
            .include(dependency("com.mysql", "mysql-connector-j", version(8,0,33)))
            .include(dependency("org.mariadb.jdbc", "mariadb-java-client", version(3,1,4)))
            .include(dependency("org.hsqldb", "hsqldb", version(2,7,1)))
            .include(dependency("org.apache.derby", "derby", version("10.16.1.1")))
            .include(dependency("org.apache.derby", "derbytools", version("10.16.1.1")))
            .include(dependency("com.oracle.database.jdbc", "ojdbc11", version("23.2.0.0")))
            .include(dependency("org.json", "json", version(20230227)));

        cleanOperation()
            .directories(
                new File(workDirectory(), "embedded_dbs"),
                new File(workDirectory(), "logs"));

        var core_directory = new File(workDirectory(), "core");
        var core_src_directory = new File(core_directory, "src");
        var core_src_main_directory = new File(core_src_directory, "main");
        var core_src_main_java_directory = new File(core_src_main_directory, "java");
        var core_src_main_resources_directory = new File(core_src_main_directory, "resources");
        var core_src_test_directory = new File(core_src_directory, "test");
        var core_src_test_java_directory = new File(core_src_test_directory, "java");
        var core_src_test_resources_directory = new File(core_src_test_directory, "resources");
        var core_src_main_resources_templates_directory = new File(core_src_main_resources_directory, "templates");

        antlr4Operation
            .sourceDirectories(List.of(new File(core_src_main_directory, "antlr")))
            .outputDirectory(new File(buildDirectory(), "generated/rife/template/antlr"))
            .visitor()
            .longMessages();

        precompileOperation()
            .sourceDirectories(core_src_main_resources_templates_directory)
            .templateTypes(HTML, XML, SQL, TXT, JSON);

        compileOperation()
            .mainSourceDirectories(antlr4Operation.outputDirectory(), core_src_main_java_directory)
            .testSourceDirectories(core_src_test_java_directory)
            .compileOptions()
                .debuggingInfo(JavacOptions.DebuggingInfo.ALL);

        jarOperation()
            .sourceDirectories(core_src_main_resources_directory)
            .excluded(Pattern.compile("^\\Q" + core_src_main_resources_templates_directory.getAbsolutePath() + "\\E.*"))
            .manifestAttribute(Attributes.Name.MAIN_CLASS, mainClass());

        zipBldOperation
            .destinationDirectory(buildDistDirectory())
            .destinationFileName("rife2-" + version() + "-bld.zip");

        testsBadgeOperation
            .classpath(core_src_main_resources_directory.getAbsolutePath())
            .classpath(core_src_test_resources_directory.getAbsolutePath());
        propagateJavaProperties(testsBadgeOperation.javaOptions(),
            "test.postgres",
            "test.mysql",
            "test.mariadb",
            "test.oracle",
            "test.oracle-free",
            "test.derby",
            "test.hsqldb",
            "test.h2");

        javadocOperation()
            .excluded(
                "rife/antlr/",
                "rife/asm/",
                "rife/.*/databasedrivers/",
                "rife/.*/imagestoredrivers/",
                "rife/.*/rawstoredrivers/",
                "rife/.*/textstoredrivers/",
                "rife/database/capabilities/"
            )
            .javadocOptions()
                .docTitle("<a href=\"https://rife2.com/bld\">bld</a> " + version())
                .docLint(NO_MISSING)
                .keywords()
                .splitIndex()
                .tag("apiNote", "a", "API Note:")
                .link("https://jakarta.ee/specifications/servlet/5.0/apidocs/")
                .link("https://jsoup.org/apidocs/")
                .overview(new File(srcMainJavaDirectory(), "overview.html"));

        publishOperation()
            .repository(version.isSnapshot() ? repository("rife2-snapshots") : repository("rife2-releases"))
            .repository(version.isSnapshot() ? repository("sonatype-snapshots") : repository("sonatype-releases"))
            .info(new PublishInfo()
                .groupId("com.uwyn.rife2")
                .artifactId("bld")
                .name("bld")
                .description("Pure java build tool for developers who don't like dealing with build tools.")
                .url("https://github.com/rife2/bld")
                .developer(new PublishDeveloper()
                    .id("gbevin")
                    .name("Geert Bevin")
                    .email("gbevin@uwyn.com")
                    .url("https://github.com/gbevin"))
                .license(new PublishLicense()
                    .name("The Apache License, Version 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
                .scm(new PublishScm()
                    .connection("scm:git:https://github.com/rife2/bld.git")
                    .developerConnection("scm:git:git@github.com:rife2/bld.git")
                    .url("https://github.com/rife2/bld"))
                .signKey(property("sign.key"))
                .signPassphrase(property("sign.passphrase")))
            .artifacts(
                new PublishArtifact(zipBldOperation.destinationFile(), "bld", "zip"));
    }

    void propagateJavaProperties(JavaOptions options, String... names) {
        for (var name : names) {
            if (properties().contains(name)) {
                options.property(name, properties().getValueString(name));
            }
        }
    }

    final Antlr4Operation antlr4Operation = new Antlr4Operation() {
        @Override
        public void execute()
        throws Exception {
            super.execute();
            // replace the package name so that it becomes part of RIFE2
            FileUtils.transformFiles(outputDirectory(), FileUtils.JAVA_FILE_PATTERN, null, s ->
                StringUtils.replace(s, "org.antlr.v4.runtime", "rife.antlr.v4.runtime"));
        }
    };
    @BuildCommand(summary = "Generates the grammar Java sources")
    public void generateGrammar()
    throws Exception {
        antlr4Operation.executeOnce();
    }

    public void compile()
    throws Exception {
        generateGrammar();
        super.compile();
    }

    final ZipOperation zipBldOperation = new ZipOperation();
    @BuildCommand(value = "zip-bld", summary = "Creates the bld zip archive")
    public void zipBld()
    throws Exception {
        jar();
        var tmp = Files.createTempDirectory("bld").toFile();
        try {
            new Wrapper().createWrapperFiles(path(tmp, "lib").toFile(), VersionNumber.UNKNOWN.toString());
            new DirBuilder(tmp, t -> {
                t.dir("bld", b -> {
                    b.dir("bin", i -> {
                        i.file("bld", f -> {
                            f.copy(path(srcMainDirectory(), "bld", "bld"));
                            f.perms(0755);
                        });
                        i.file("bld.bat", f -> {
                            f.copy(path(srcMainDirectory(), "bld", "bld.bat"));
                            f.perms(0755);
                        });
                    });
                    b.dir("lib", l -> {
                        l.file("bld-wrapper.jar", f -> f.move(path(tmp, "lib", "bld-wrapper.jar")));
                    });
                });
                t.dir("lib", l -> l.delete());
            });

            zipBldOperation
                .sourceDirectories(tmp)
                .execute();
        } finally {
            FileUtils.deleteDirectory(tmp);
        }
    }

    private final TestsBadgeOperation testsBadgeOperation = new TestsBadgeOperation();
    public void test()
    throws Exception {
        testsBadgeOperation.executeOnce(() -> testsBadgeOperation
            .url(property("testsBadgeUrl"))
            .apiKey(property("testsBadgeApiKey"))
            .fromProject(this));
    }

    @BuildCommand(summary = "Creates all the distribution artifacts")
    public void all()
    throws Exception {
        jar();
        jarSources();
        jarJavadoc();
        zipBld();
    }

    public void publish()
    throws Exception {
        all();
        super.publish();
    }

    public static void main(String[] args)
    throws Exception {
        new BldBuild().start(args);
    }
}