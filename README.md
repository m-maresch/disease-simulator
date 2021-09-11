# Disease Simulator
This repository contains a Disease Simulation software implemented using a Reactive Architecture. The projects consists of a Web-App, an API Gateway and a Disease Simulation service. The Web-App talks to the API Gateway using RSocket. The Disease Simulation service runs the actual simulation. The Gateway requests a new simulation from the Disease Simulation service and this service publishes messages to a RabbitMQ queue. The contents of this queue are than delivered back to the frontend by the API Gateway via RSocket. 

The simulation is implemented as a discrete event simulation. The actor model is used to implement a multi-agent system. In this system, a user-supplied number of individuals and their interactions are continuously simulated. In the beginning, a user-supplied number of individuals are considered infected from the beginning and the disease spreads from there. On each interaction between an non-infected individual and an infected individual, there is a user-supplied probability that the disease will spread to the non-infected individual of that interaction. The individuals interact with other individuals randomly based on one dimensional proximity.

Thus, the simulation itself is based on 3 arguments which come from the user: 
- How many individuals are infected from the beginning
- How many individuals are to be simulated
- How likely infections should be on interactions between individuals

The Web-App is implemented using React. In this app, the user enters the 3 arguments and submits them to start a new simulation. A chart is used to visualize the number of infections each simulated individual has caused in realtime.

The Reactive Architecture is based on RSocket, Reactor and Akka:
- RSocket is used for the communication between the Web-App and the API Gateway
- Reactor and Spring are used in the API Gateway
- Akka Typed is used in the Disease Simulation service along with Akka HTTP and Akka Streams

If you have any questions about the applications or you'd like to know how to run them then feel free to contact me via [mmaresch.com](mmaresch.com).

# Dependencies
Thanks to everyone contributing to any of the following projects:
- Any Spring project
- Any Akka project
- Lombok
- Reactor
- SLF4J
- React
- Chart.js
- RSocket-JS
- React-Chartjs-2
