package akka.contrib.persistence.mongodb

import scala.collection.immutable.Seq
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.PersistentRepr
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import akka.persistence.PersistentConfirmation
import akka.persistence.PersistentId

class MongoJournal extends AsyncWriteJournal {
  
  private[this] val impl = context.system.extension(MongoPersistenceExtensionId).journaler
  private[this] implicit val ec = context.dispatcher

  /**
   * Plugin API: asynchronously writes a batch of persistent messages to the journal.
   * The batch write must be atomic i.e. either all persistent messages in the batch
   * are written or none.
   */
  override def asyncWriteMessages(messages: Seq[PersistentRepr]): Future[Unit] = 
    impl.appendToJournal(messages)

  /**
   * Plugin API: asynchronously writes a batch of delivery confirmations to the journal.
   */
  override def asyncWriteConfirmations(confirmations: Seq[PersistentConfirmation]): Future[Unit] = 
    impl.confirmJournalEntries(confirmations)

  /**
   * Plugin API: asynchronously deletes messages identified by `messageIds` from the
   * journal. If `permanent` is set to `false`, the persistent messages are marked as
   * deleted, otherwise they are permanently deleted.
   */
  override def asyncDeleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Future[Unit] = 
    impl.deleteAllMatchingJournalEntries(messageIds, permanent)

  /**
   * Plugin API: asynchronously deletes all persistent messages up to `toSequenceNr`
   * (inclusive). If `permanent` is set to `false`, the persistent messages are marked
   * as deleted, otherwise they are permanently deleted.
   */
  override def asyncDeleteMessagesTo(processorId: String, toSequenceNr: Long, permanent: Boolean): Future[Unit] = 
    impl.deleteJournalEntries(processorId, 0L, toSequenceNr, permanent)

  /**
   * Plugin API: asynchronously replays persistent messages. Implementations replay
   * a message by calling `replayCallback`. The returned future must be completed
   * when all messages (matching the sequence number bounds) have been replayed.
   * The future must be completed with a failure if any of the persistent messages
   * could not be replayed.
   *
   * The `replayCallback` must also be called with messages that have been marked
   * as deleted. In this case a replayed message's `deleted` method must return
   * `true`.
   *
   * The channel ids of delivery confirmations that are available for a replayed
   * message must be contained in that message's `confirms` sequence.
   *
   * @param processorId processor id.
   * @param fromSequenceNr sequence number where replay should start (inclusive).
   * @param toSequenceNr sequence number where replay should end (inclusive).
   * @param max maximum number of messages to be replayed.
   * @param replayCallback called to replay a single message. Can be called from any
   *                       thread.
   *
   * @see [[AsyncWriteJournal]]
   * @see [[SyncWriteJournal]]
   */
  override def asyncReplayMessages(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: PersistentRepr ⇒ Unit): Future[Unit] = 
  	impl.replayJournal(processorId, fromSequenceNr, toSequenceNr)(replayCallback)

  /**
   * Plugin API: asynchronously reads the highest stored sequence number for the
   * given `processorId`.
   *
   * @param processorId processor id.
   * @param fromSequenceNr hint where to start searching for the highest sequence
   *                       number.
   */
  override def asyncReadHighestSequenceNr(processorId: String, fromSequenceNr: Long): Future[Long] = 
    impl.maxSequenceNr(processorId, fromSequenceNr)

}

object JournallingFieldNames {
  final val PROCESSOR_ID = "pid"
  final val SEQUENCE_NUMBER = "sn"
  final val CONFIRMS = "cs"
  final val DELETED = "dl"
  final val SERIALIZED = "pr"
}

trait MongoPersistenceJournallingApi {
  private[mongodb] def journalEntry(pid: String, seq: Long)(implicit ec: ExecutionContext): Future[Option[PersistentRepr]]

  private[mongodb] def journalRange(pid: String, from: Long, to: Long)(implicit ec: ExecutionContext): Future[Iterator[PersistentRepr]]
  
  private[mongodb] def appendToJournal(persistent: TraversableOnce[PersistentRepr])(implicit ec: ExecutionContext): Future[Unit]

  private[mongodb] def deleteJournalEntries(pid: String, from: Long, to: Long, permanent: Boolean)(implicit ec: ExecutionContext): Future[Unit]

  private[mongodb] def deleteAllMatchingJournalEntries(ids: Seq[PersistentId], permanent: Boolean)(implicit ec: ExecutionContext): Future[Unit]

  private[mongodb] def confirmJournalEntries(confirms: Seq[PersistentConfirmation])(implicit ec: ExecutionContext): Future[Unit]
  
  private[mongodb] def replayJournal(pid: String, from: Long, to: Long)(replayCallback: PersistentRepr ⇒ Unit)(implicit ec: ExecutionContext): Future[Unit]
  
  private[mongodb] def maxSequenceNr(pid: String, from: Long)(implicit ec: ExecutionContext): Future[Long]
}
  