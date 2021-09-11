import React, { Component } from 'react';
import {RSocketClient, JsonSerializer, IdentitySerializer} from 'rsocket-core';
import RSocketWebSocketClient from 'rsocket-websocket-client';
import {Bar} from 'react-chartjs-2';

class App extends Component {
  constructor(props) {
    super()
    this.state = {
      individuals: [],
      infectedCounts: [],
      totalNumberOfInfected: 0,
      totalNumberOfInteractions: 0,
      avgNumberOfInteractions: 0,
      config: ''
    }

    this.handleChange = this.handleChange.bind(this)
    this.handleSubmit = this.handleSubmit.bind(this)
  }

  startConnection = async (options) => {
    const socket = await this.connectToGateway(options)
    let subscription
    let pending = 5

    socket.requestStream({
      data: { 
        initialNumberOfInfected: options.config[0],
        numberOfPeople: options.config[1],
        probabilityOfInfection: options.config[2]
      },
      metadata: String.fromCharCode('start-simulation'.length) + 'start-simulation',
    }).subscribe({
      onComplete: () => {
        console.log('Complete')
      },
      onError: (error)  => {
        console.log('Error: %s', error.message)
      },
      onNext: (payload) => {
        console.log('Next: %s', payload.data)
        if (--pending === 0) {
          pending = 5;
          subscription.request(pending)
        }

        let config = this.state.config.split(',')
        let initialNumberOfInfected = parseInt(config[0])
        let numberOfPeople = parseInt(config[1])

        let from = payload.data.from;

        let numberOfInteractions = payload.data.numberOfInteractions <= 1 ? 0 : payload.data.numberOfInteractions
        let totalNumberOfInteractions = this.state.totalNumberOfInteractions + numberOfInteractions

        let infectedCounts = this.state.infectedCounts
        infectedCounts[from]++
        this.setState({infectedCounts: infectedCounts.slice(0)})

        let infectedCountsSum = infectedCounts.reduce((pv, cv) => pv + cv)
        this.setState({totalNumberOfInfected: infectedCountsSum})
        this.setState({totalNumberOfInteractions})

        if (infectedCountsSum > initialNumberOfInfected) {
          this.setState({avgNumberOfInteractions: (totalNumberOfInteractions / (infectedCountsSum - initialNumberOfInfected))})
        }

        if (infectedCountsSum >= Math.floor(numberOfPeople * 0.99)) {
          subscription.cancel()
        }
      },
      onSubscribe(_subscription) {
        subscription = _subscription
        subscription.request(pending)
      },
    });
  }
  
  connectToGateway = async (options) => {
    const client = new RSocketClient({
      serializers: {
        data: JsonSerializer,
        metadata: IdentitySerializer
      },
      setup: {
        dataMimeType: 'application/json',
        keepAlive: 60000,
        lifetime: 180000,
        metadataMimeType: 'message/x.rsocket.routing.v0'
      },
      transport: new RSocketWebSocketClient({
        url: "ws:\\\\" + options.host + ":" + options.port + "\\rsocket"
      }),
    })
    return await client.connect()
  }

  handleChange(event) {
    this.setState({config: event.target.value})
  }

  handleSubmit(event) {
    let config = new Array(3)
    config[0] = "5"
    config[1] = "100"
    config[2] = "10"

    if (this.state.config) {
      config = this.state.config.split(',')
    }

    let initialNumberOfInfected = parseInt(config[0])
    let numberOfPeople = parseInt(config[1])
    let probabilityOfInfection = parseInt(config[2])

    if (numberOfPeople > 2000) {
      config[1] = "100";
    }
    
    if (initialNumberOfInfected > numberOfPeople) {
      config[0] = "5";
      if (numberOfPeople < 5) {
        config[1] = "5";
      }
    }

    if (probabilityOfInfection > 100) {
      config[2] = "10";
    }

    this.setState({config: config.join()})
    
    numberOfPeople = parseInt(config[1]) + 1

    let infectedCounts = Array(numberOfPeople).fill(0)
    let individuals = Array.from(Array(numberOfPeople).keys()).map(key => "#" + key)
    individuals[0] = "Initial"

    this.setState({infectedCounts})
    this.setState({totalNumberOfInfected: 0})
    this.setState({totalNumberOfInteractions: 0})
    this.setState({avgNumberOfInteractions: 0})
    this.setState({individuals}, async () => {
      await this.startConnection({host: "localhost", port: 7000, config})
    })

    event.preventDefault()
  }

  render() {
    return (
      <div>
        <header>
        </header>
        <div style={{margin: "1em"}}>
          <h2>
              Disease Simulator
          </h2>
          <div>
            <form onSubmit={this.handleSubmit}>
              <label>
                Configuration: 
                <input type="text" 
                  placeholder="Initial number of infected, number of people, probability of infection - e.g. 5,100,10" 
                  value={this.state.config} 
                  onChange={this.handleChange}
                  size={75}
                  style={{margin: "1em", padding: "4px", fontSize: "16px"}}
                />
              </label>
              <input type="submit" value="Submit" style={{
                color:"white", 
                backgroundColor: "indigo", 
                borderRadius: "8px", 
                border: "none",
                padding: "8px",
                fontSize: "16px"
                }}/>
            </form>
          </div>
          <div style={{position: "relative", margin: "auto", width: "70vw"}}>
            <Bar 
            data={{
              labels: this.state.individuals,
              datasets: [{
                label: '# of Infected',
                data: this.state.infectedCounts,
                backgroundColor: 'rgb(200, 0, 0, 0.3)',
                borderColor: 'rgb(200, 0, 0, 0.9)',
                borderWidth: 2
              }]
            }} 
            options={{
              animation: {
                duration: 0
            },
              scales: {
                y: {
                    max: 10,
                    min: 0
                }
            },
            scaleShowValues: true,
            }}></Bar>
          </div>
          <p style={{textAlign: "center"}}>Total number of infected: <b>{this.state.totalNumberOfInfected}</b></p>
          <p style={{textAlign: "center"}}>Average number of interactions until infection: <b>{Math.ceil(this.state.avgNumberOfInteractions)}</b></p>
        </div>
      </div>
    )
  }
}

export default App;
