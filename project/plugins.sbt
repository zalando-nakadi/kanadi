addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"       % "2.4.4")
addSbtPlugin("com.github.sbt"                    % "sbt-pgp"            % "2.1.2")
addSbtPlugin("org.xerial.sbt"                    % "sbt-sonatype"       % "3.9.10")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings"   % "3.0.0")
addSbtPlugin("org.scoverage"                     % "sbt-scoverage"      % "1.9.2")
addSbtPlugin("org.scoverage"                     % "sbt-coveralls"      % "1.3.1")
addSbtPlugin("com.codecommit"                    % "sbt-github-actions" % "0.13.0")
addSbtPlugin("com.github.sbt"                    % "sbt-release"        % "1.1.0")
addSbtPlugin("com.github.sbt"                    % "sbt-avro"           % "3.4.0")

libraryDependencies += "org.apache.avro" % "avro-compiler" % "1.11.0"
