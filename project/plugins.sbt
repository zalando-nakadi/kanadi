addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"       % "2.5.0")
addSbtPlugin("com.github.sbt"                    % "sbt-pgp"            % "2.2.1")
addSbtPlugin("org.xerial.sbt"                    % "sbt-sonatype"       % "3.9.21")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings"   % "3.0.2")
addSbtPlugin("org.scoverage"                     % "sbt-scoverage"      % "2.0.8")
addSbtPlugin("org.scoverage"                     % "sbt-coveralls"      % "1.3.9")
addSbtPlugin("com.codecommit"                    % "sbt-github-actions" % "0.14.2")
addSbtPlugin("com.github.sbt"                    % "sbt-release"        % "1.1.0")
addSbtPlugin("com.timushev.sbt"                  % "sbt-updates"        % "0.6.4")
addSbtPlugin("com.github.sbt"                    % "sbt-avro"           % "3.4.2")

libraryDependencies += "org.apache.avro" % "avro-compiler" % "1.11.0"
