// Deliberately its own build, not a module of uxmEssentials. A module would resolve the API through a project
// dependency and prove nothing: the whole point of this build is to consume the published artifacts the same way
// a stranger's plugin does, through a repository and a coordinate.
rootProject.name = "uxmessentials-sample-consumer"
