package de.pc2.dedup.fschunk.handler.direct

import scala.collection.mutable._
import de.pc2.dedup.util.FileSizeCategory
import de.pc2.dedup.chunker._
import de.pc2.dedup.fschunk.parse._
import java.io.BufferedWriter
import java.io.FileWriter
import scala.actors.Actor
import de.pc2.dedup.util.StorageUnit
import de.pc2.dedup.fschunk.handler.FileDataHandler
import de.pc2.dedup.util.Log
import scala.math.Ordering
import java.nio.ByteBuffer
import de.pc2.dedup.chunker.rabin._
/**
 * Handler to search for the zero-chunk in the trace
 */
class ZeroChunkDeduplicationHandler() extends FileDataHandler with Log {
  var lock: AnyRef = new Object()
  val zeroIndex = Set.empty[Digest]

  /**
   * Helper method to calculate the SHA-1 of the zero chunk
   */
  def getFullChunkDigest(v: Byte, maxSize: Int, digestSize: Int): Digest = {
    val bb = ByteBuffer.allocate(maxSize * 2)
    while (bb.remaining > 0) {
      bb.put(v)
    }
    bb.rewind()
    var chunk: Chunk = null
    val rc = new RabinChunker(2 * 1024, 8 * 1024, maxSize, false, new DigestFactory("SHA-1", digestSize), "c8")
    val rcs = rc.createSession()
    rcs.chunk(bb) { c: Chunk =>
      if (chunk == null) {
        chunk = c
      }
    }
    if (chunk == null) {
      rcs.close() { c: Chunk =>
        if (chunk == null) {
          chunk = c
        }
      }
    }
    chunk.fp
  }

  zeroIndex += getFullChunkDigest(0, 8 * 1024, 20)
  zeroIndex += getFullChunkDigest(0, 16 * 1024, 20)
  zeroIndex += getFullChunkDigest(0, 32 * 1024, 20)
  zeroIndex += getFullChunkDigest(0, 8 * 1024, 10)
  zeroIndex += getFullChunkDigest(0, 16 * 1024, 10)
  zeroIndex += getFullChunkDigest(0, 32 * 1024, 10)

  var totalSize = 0L
  var redundantSize = 0L
  var chunkCount = 0L
  var zeroChunkCount = 0L

  def handle(fp: FilePart) {
    lock.synchronized {

      for (chunk <- fp.chunks) {
        totalSize += chunk.size
        chunkCount += 1

        if (zeroIndex.contains(chunk.fp)) {
          redundantSize += chunk.size
          zeroChunkCount += 1
        }
      }

    }
  }

  def handle(f: File) {
    lock.synchronized {
      logger.debug("Handle file %s".format(f.filename))

      for (chunk <- f.chunks) {
        totalSize += chunk.size
        chunkCount += 1

        if (zeroIndex.contains(chunk.fp)) {
          redundantSize += chunk.size
          zeroChunkCount += 1
        }
      }
    }
  }

  override def report() {
    outputMapToConsole()
  }

  override def quit() {
    outputMapToConsole()
  }

  private def outputMapToConsole() {
    lock.synchronized {
      def storageUnitIfPossible(k: Long): String = {
        try {
          return StorageUnit(k).toString() + "B"
        } catch {
          case _ =>
          // pass
        }
        return k.toString()
      }

      val ratio = 1.0 * redundantSize / totalSize

      println("Total size: %s, %s".format(storageUnitIfPossible(totalSize), totalSize))
      println("Redundant size: %s, %s".format(storageUnitIfPossible(redundantSize), redundantSize))
      println("Deduplication Ratio: %s ".format(ratio * 100.0))
      println()
      println("Total chunk count: %s".format(chunkCount))
      println("Zero chunk count: %s".format(zeroChunkCount))
    }
  }
}