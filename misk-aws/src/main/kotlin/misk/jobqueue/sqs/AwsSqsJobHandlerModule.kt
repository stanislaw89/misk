package misk.jobqueue.sqs

import com.google.common.util.concurrent.AbstractIdleService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Install this module to register a handler for an SQS queue,
 * along with its corresponding retry queue
 */
class AwsSqsJobHandlerModule<T : JobHandler> private constructor(
  private val queueName: QueueName,
  private val handler: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)
    newMapBinder<QueueName, JobHandler>().addBinding(queueName.retryQueue).to(handler.java)
    install(ServiceModule<AwsSqsJobHandlerSubscriptionService>())
  }

  companion object {
    inline fun <reified T : JobHandler> create(queueName: QueueName):
        AwsSqsJobHandlerModule<T> = create(queueName, T::class)

    @JvmStatic
    fun <T : JobHandler> create(
      queueName: QueueName,
      handlerClass: Class<T>
    ): AwsSqsJobHandlerModule<T> {
      return create(queueName, handlerClass.kotlin)
    }

    /**
     * Returns a module that registers a handler for an SQS queue.
     */
    fun <T : JobHandler> create(
      queueName: QueueName,
      handlerClass: KClass<T>
    ): AwsSqsJobHandlerModule<T> {
      return AwsSqsJobHandlerModule(queueName, handlerClass)
    }
  }
}

@Singleton
internal class AwsSqsJobHandlerSubscriptionService @Inject constructor(
  private val attributeImporter: AwsSqsQueueAttributeImporter,
  private val consumer: SqsJobConsumer,
  private val consumerMapping: Map<QueueName, JobHandler>,
  private val externalQueues: Map<QueueName, AwsSqsQueueConfig>
) : AbstractIdleService() {
  override fun startUp() {
    consumerMapping.forEach { consumer.subscribe(it.key, it.value) }
    externalQueues.forEach { attributeImporter.import(it.key) }
  }

  override fun shutDown() {}
}
