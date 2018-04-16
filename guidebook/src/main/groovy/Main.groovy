import com.structurizr.Workspace
import com.structurizr.api.StructurizrClient
import com.structurizr.model.Container
import com.structurizr.model.InteractionStyle
import com.structurizr.model.Person
import com.structurizr.model.SoftwareSystem

/**
 * Simple driver to build up the Structurizr model.
 */
class Main {

    static void main(String[] args) {

        def workspace = new Workspace('ADP', 'This is a model of the ADP system.' )

        def batchProcess = workspace.model.addPerson('Customer', "Customer's batch process" )
        def slurpe = workspace.model.addSoftwareSystem('ADP', 'Tracks published lessons' )
        def authoring = workspace.model.addSoftwareSystem('Sush-E', 'Manages lesson authoring' )
        def tlo = workspace.model.addSoftwareSystem('TLO', 'Manages language learning' )
        def coordinator = workspace.model.addSoftwareSystem('RSS Coordinator', 'Transforms manually tagged lessons into publication events' )

        def eventProcessor = slurpe.addContainer( 'Event Processor', 'Transforms events into publication information', 'Groovy/Spring Cloud Stream/Spring Data MongoDB' )
        def journaler = slurpe.addContainer('Journaler', 'Copies events to temporary storage', 'Groovy/Spring Data MongoDB/Avro' )
        def archiver = slurpe.addContainer('Archiver', 'Moves events to archival storage', 'Groovy/Spring Data MongoDB/Avro' )
        def apiServer = slurpe.addContainer('API Server', 'Handles interaction with batch processor', 'Groovy/Spring Data REST' )
        def apiGateway = slurpe.addContainer('API Gateway', 'Manages API keys and throttling', 'SaaS' )
        def rabbitMQ = slurpe.addContainer('RabbitMQ', 'Brokers events', 'RabbitMQ' )
        def mongoDB = slurpe.addContainer('MongoDB', 'Stores publication information',  'MongoDB' )
        def mySQL = slurpe.addContainer('MySQL', 'Stores batch job information',  'MySQL' )
        def redis = slurpe.addContainer('Redis', 'Stores inter-step batch job data',  'Redis' )
        def reprocessor = slurpe.addContainer('Reprocessor', 'Replays past events, reconstructing publication information', 'Groovy/Avro/Spring Cloud Stream' )
        def s3 = slurpe.addContainer( 'S3', 'Stores archived events', 'SaaS')
        def glacier = slurpe.addContainer( 'Glacier', 'Cold storage of archived events', 'SaaS')
        def cloudwatch = slurpe.addContainer( 'CloudWatch', 'Centralized logging', 'SaaS')

        constructContextView(workspace, slurpe, batchProcess, authoring, tlo, coordinator)
        constructProcessorContainerView(workspace, slurpe, eventProcessor, rabbitMQ, mongoDB, authoring, coordinator )
        constructApiServerContainerView(workspace, slurpe, batchProcess, apiGateway, apiServer, mongoDB)
        constructArchiverContainerView(workspace, slurpe, archiver, s3, mongoDB, glacier, mySQL, redis)
        constructJournalerContainerView(workspace, slurpe, journaler, rabbitMQ, mongoDB)
        constructReprocessorContainerView(workspace, slurpe, reprocessor, s3, glacier, rabbitMQ)
        constructSlurpeDeploymentView(workspace, slurpe, apiServer, eventProcessor, archiver, reprocessor, journaler)
        constructSaasDeploymentView(workspace, slurpe, rabbitMQ, mongoDB, apiGateway, cloudwatch)

        publishDiagrams(workspace)
    }

    private static void publishDiagrams(Workspace workspace) {
        def env = System.getenv()
        def apiKey = Optional.ofNullable(env['API_KEY']).orElseThrow({
            new IllegalStateException('API_KEY not in environment')
        })
        def apiSecret = Optional.ofNullable(env['API_SECRET']).orElseThrow({
            new IllegalStateException('API_SECRET not in environment')
        })
        long workspaceId = Optional.ofNullable(env['WORKSPACE_ID']).orElseThrow({
            new IllegalStateException('WORKSPACE_ID not in environment')
        }).toLong()
        def structurizr = new StructurizrClient(apiKey, apiSecret)
        structurizr.putWorkspace(workspaceId, workspace)
    }

    private static void constructSaasDeploymentView(Workspace workspace, SoftwareSystem slurpe, Container rabbitMQ, Container mongoDB, Container apiGateway, Container cloudwatch) {
        def view = workspace.views.createDeploymentView(slurpe, 'SaaS', 'Hosted services')
        def rabbitNode = workspace.model.addDeploymentNode('RabbitMQ', 'Hosted RabbitMQ', 'EC2')
        def mongoNode = workspace.model.addDeploymentNode('MongoDB', 'Hosted MongoDB', 'EC2')
        def gatewayNode = workspace.model.addDeploymentNode('API Gateway', 'Hosted API Gateway', 'Amazon API Gateway')
        def cloudwatchNode = workspace.model.addDeploymentNode('CloudWatch', 'Hosted logging service', 'Amazon CloudWatch')
        rabbitNode.add(rabbitMQ)
        mongoNode.add(mongoDB)
        gatewayNode.add(apiGateway)
        cloudwatchNode.add(cloudwatch)
        view.add(rabbitNode)
        view.add(mongoNode)
        view.add(gatewayNode)
        view.add(cloudwatchNode)
    }

    private static void constructSlurpeDeploymentView(Workspace workspace, SoftwareSystem slurpe, Container apiServer, Container eventProcessor, Container archiver, Container reprocessor, Container journaler) {
        def view = workspace.views.createDeploymentView(slurpe, 'ADP', 'ADP deployment')
        def ec2 = workspace.model.addDeploymentNode('Elastic Compute Cloud', 'Virtual machine running Docker', 'Ubuntu/Docker')
        def docker = ec2.addDeploymentNode('Docker', 'Runs Docker containers', 'Docker')
        docker.add(apiServer)
        docker.add(eventProcessor)
        docker.add(archiver)
        docker.add(reprocessor)
        docker.add(journaler)
        view.add(docker)
        view.add(ec2)
    }

    private static void constructReprocessorContainerView(Workspace workspace, SoftwareSystem slurpe, Container reprocessor, Container s3, Container glacier, Container rabbitMQ) {
        def view = workspace.views.createContainerView(slurpe, "Reprocessor", 'Copies archived events into RabbitMQ for reprocessing')
        reprocessor.uses(s3, 'Reads events from storage', 'AWS SDK/Avro', InteractionStyle.Synchronous)
        reprocessor.uses(glacier, 'Reads events from cold storage', 'AWS SDK/Avro', InteractionStyle.Synchronous)
        reprocessor.uses(rabbitMQ, 'Pushes events to the reprocessing exchange', 'Spring Cloud Stream/Avro', InteractionStyle.Asynchronous)
        view.add(reprocessor)
        view.add(s3)
        view.add(glacier)
        view.add(rabbitMQ)
    }

    private static void constructArchiverContainerView(Workspace workspace, SoftwareSystem slurpe, Container archiver, Container s3, Container mongoDB, Container glacier, Container mysql, Container redis) {
        def view = workspace.views.createContainerView(slurpe, "Archiver", 'Moves raw events to archival storage')
        archiver.uses(s3, 'moves events to', 'Avro/AWS SDK', InteractionStyle.Synchronous)
        archiver.uses(mongoDB, 'Pulls buffered messages', 'Spring Data MongoDB')
        archiver.uses(mysql, 'Stores job state', 'Spring Batch')
        archiver.uses(redis, 'Stores shared, inter-step data', 'Spring Batch')
        s3.uses( glacier, 'Moves Avro encoded event files to cold storage', 'automated schedule')
        view.add(archiver)
        view.add(s3)
        view.add(mongoDB)
        view.add(glacier)
        view.add(mysql)
        view.add(redis)
    }

    private static void constructJournalerContainerView(Workspace workspace, SoftwareSystem slurpe, Container journaler, Container rabbitMQ, Container mongoDB) {
        def view = workspace.views.createContainerView(slurpe, "Journaler", 'Copies raw events to temporary storage')
        journaler.uses(rabbitMQ, 'Copies events from', 'Avro/Spring Cloud Stream', InteractionStyle.Asynchronous)
        journaler.uses(mongoDB, 'Stores raw events in', 'Spring Data MongoDB')
        view.add(journaler)
        view.add(rabbitMQ)
        view.add(mongoDB)
    }

    private static void constructApiServerContainerView(Workspace workspace, SoftwareSystem slurpe, Person batchProcess, Container apiGateway, Container apiServer, Container mongoDB) {
        def view = workspace.views.createContainerView(slurpe, "API Server", 'Returns the current publication information')
        batchProcess.uses(apiGateway, 'Get current lesson publications', 'HAL+JSON over HTTPS', InteractionStyle.Synchronous)
        apiGateway.uses(apiServer, 'Forwards traffic after TLS termination and authentication', 'HAL+JSON over HTTP', InteractionStyle.Synchronous)
        apiServer.uses(mongoDB, 'Read current lesson publication information', 'MongoDB Java driver', InteractionStyle.Synchronous)
        view.add(batchProcess)
        view.add(apiGateway)
        view.add(apiServer)
        view.add(mongoDB)
    }

    private static void constructProcessorContainerView(Workspace workspace, SoftwareSystem slurpe, Container eventProcessor, Container rabbitMQ, Container mongoDB, SoftwareSystem authoring, SoftwareSystem coordinator ) {
        def view = workspace.views.createContainerView(slurpe, 'Processor', 'Transforms events into publication data')
        eventProcessor.uses(rabbitMQ, 'Continually gets new publication events', 'Avro over AMQP', InteractionStyle.Asynchronous)
        eventProcessor.uses(mongoDB, 'Stores publication information', 'MongoDB Java driver', InteractionStyle.Synchronous)
        authoring.uses(rabbitMQ, 'Continually sends new publication events', 'Avro over AMQP', InteractionStyle.Asynchronous)
        coordinator.uses(rabbitMQ, 'Continually sends new publication events', 'Avro over AMQP', InteractionStyle.Asynchronous)
        view.add(authoring)
        view.add(rabbitMQ)
        view.add(mongoDB)
        view.add(eventProcessor)
        view.add( coordinator )
    }

    private static void constructContextView(Workspace workspace, SoftwareSystem slurpe, Person batchProcess, SoftwareSystem authoring, SoftwareSystem tlo, SoftwareSystem coordinator) {
        def view = workspace.views.createSystemContextView(slurpe, "SystemContext", "ADP in the context of its major actors.")
        slurpe.delivers( batchProcess, 'Provides publication information', 'HAL+JSON over HTTPS', InteractionStyle.Synchronous )
        authoring.uses(slurpe, 'Sends publication updates', 'Avro over AMQP', InteractionStyle.Asynchronous)
        tlo.uses( coordinator, 'Sends lessons tagged for manual publication', 'JSON over HTTP', InteractionStyle.Synchronous )
        coordinator.uses( slurpe, 'Sends publication updates', 'Avro over AMQP', InteractionStyle.Asynchronous )
        view.add(batchProcess)
        view.add(slurpe)
        view.add(authoring)
        view.add(tlo)
        view.add(coordinator)
    }


}
