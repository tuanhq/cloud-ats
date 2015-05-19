name := "cloud-ats-webui"

version := "0.1-SNAPSHOT"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
	cache,
	"org.ats" % "cloud-common" % "0.1-SNAPSHOT",
	"org.ats" % "cloud-cloudstack" % "0.1-SNAPSHOT",
	"org.ats" % "cloud-jenkins" % "0.1-SNAPSHOT",
	"org.ats" % "cloud-users-mgt" % "0.1-SNAPSHOT",
	"org.ats" % "cloud-gitlab" % "0.1-SNAPSHOT",
	"org.ats" % "cloud-jmeter" % "0.1-SNAPSHOT",
	"org.apache.cloudstack" % "cloud-api" % "4.3.0",
	"org.mongodb" % "mongo-java-driver" % "2.12.4",
	"com.typesafe.akka" % "akka-actor_2.10" % "2.2.4",
	//"com.microsoft.azure" % "azure-core" % "0.7.0",
	//"com.microsoft.azure" % "azure-management" % "0.7.0",
	//"com.microsoft.azure" % "azure-management-compute" % "0.7.0",
	//"com.microsoft.azure" % "azure-management-network" % "0.7.0",
	"org.apache.jclouds.driver" % "jclouds-slf4j" % "1.9.0",
	"org.apache.jclouds.driver" % "jclouds-sshj" % "1.9.0",
	"org.apache.jclouds.api" % "openstack-keystone" % "1.9.0",
	"org.apache.jclouds.api" % "openstack-nova" % "1.9.0",
	"org.apache.jclouds.api" % "openstack-swift" % "1.9.0",
	"org.apache.jclouds.api" % "openstack-cinder" % "1.9.0", 
	"org.apache.jclouds.api" % "openstack-trove" % "1.9.0",
	"org.apache.jclouds.labs" % "openstack-glance" % "1.9.0",
	"org.apache.jclouds.labs" % "openstack-marconi" % "1.9.0",
	"org.apache.jclouds.labs" % "openstack-neutron" % "1.9.0",
	"ch.qos.logback" % "logback-classic" % "1.0.13",
	"mysql" % "mysql-connector-java" % "5.1.25",
	"com.sun.jersey" % "jersey-json" % "1.13",
	"junit" % "junit" % "4.8.2"
)     

resolvers += (
	"Local Maven Repository" at "file://" + Path.userHome.absolutePath  + "/java/dependencies/repository"		
)

play.Project.playJavaSettings
