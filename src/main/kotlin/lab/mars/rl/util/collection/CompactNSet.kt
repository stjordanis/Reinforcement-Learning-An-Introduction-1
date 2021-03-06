@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package lab.mars.rl.util.collection

import lab.mars.rl.util.buf.*
import lab.mars.rl.util.exception.IndexOutOfDimensionException
import lab.mars.rl.util.tuples.tuple2
import java.util.*

/**
 * <p>
 * Created on 2017-09-18.
 * </p>
 *
 * @author wumo
 */

/**
 * build a [NSet] using [elements]
 */
fun <T: Any> cnsetOf(vararg elements: T): CompactNSet<T> {
  val set = CompactNSet<T>(Array<Any>(elements.size) { elements[it] }.buf(0, 0))
  val subtree = set.expand(0, elements.size)
  subtree.offsetEnd = set.data.writePtr - 1
  set.size
  return set
}

val emptyCNSet = CompactNSet<Any>(Array<Any>(1) {}.buf(0, -1))
inline fun <E: Any> emptyCNSet(): CompactNSet<E> = emptyCNSet as CompactNSet<E>

class CompactNSet<E: Any>
constructor(internal val data: MutableBuf<Any>, val rootOffset: Int = 0, val subLevel: Int = 0)
  : IndexedCollection<E> {
  private var _size = -1
  
  class SubTree(val size: Int, val offset2nd: Int, var offsetEnd: Int = -1)
  
  val SubTree.lastIndex: Int
    get() = size - 1
  
  /**
   * [subtrees] shouldn't be empty. If so we should use [value] instead of [Cell]
   * @param subtrees the subtree of the [Cell]
   * @param value the value of the [Cell]
   */
  class Cell<E: Any>(val subtrees: MutableBuf<SubTree>, var value: E) {
    inline operator fun get(idx: Int): SubTree {
      if (idx < 0 || idx >= subtrees.size)
        throw IndexOutOfDimensionException()
      return subtrees[idx]
    }
    
    fun copy() = Cell(subtrees, value)
  }
  
  override fun <T: Any> copycat(element_maker: (Index) -> T)
      : IndexedCollection<T> {
    val new_data = DefaultBuf.new<Any>(data.cap)
    for (a in 0..data.lastIndex)
      new_data.append((data[a] as? Cell<E>)?.copy() ?: data[a])
    return CompactNSet<T>(new_data, subLevel).apply {
      set { slot, _ ->
        element_maker(slot)
      }
      size
    }
  }
  
  private inline fun <R: Any> operation(idx: Iterator<Int>, op: (Int, Int) -> R): R {
    var offset = rootOffset
    var level = subLevel
    while (true) {
      val d = idx.next()//子树索引
      val tmp = data[offset] as? Cell<E> ?: return rootElement(d, idx) { op(offset, level) }
      if (level > tmp.subtrees.lastIndex) return rootElement(d, idx) { op(offset, level) }
      val subtree = tmp[level]
      require(d >= 0 && d < subtree.size)
      if (d == 0) {
        level++
      } else {
        level = 0
        offset = subtree.offset2nd + d - 1
      }
      if (!idx.hasNext()) break
    }
    return op(offset, level)
  }
  
  private inline fun <R: Any> rootElement(d: Int, idx: Iterator<Int>,
                                          op: () -> R): R {
    if (d == 0 && !idx.hasNext())
      return op()
    else
      throw IndexOutOfDimensionException()
  }
  
  fun location(idx: Index): Int =
      operation(idx.iterator()) { offset, _ -> offset }
  
  fun _get(idx: Int): E {
    val tmp = data[idx]
    return when (tmp) {
      is Cell<*> -> tmp.value
      else -> tmp
    } as E
  }
  
  fun _set(idx: Int, s: E) {
    val tmp = data[idx] as? Cell<E>
    if (tmp != null) tmp.value = s
    else data[idx] = s
  }
  
  override fun invoke(subset_dim: Index): IndexedCollection<E> {
    return operation(subset_dim.iterator()) { offset, level ->
      CompactNSet<E>(data, offset, level).apply { size }
    }
  }
  
  override fun at(idx: Int): E {
    require(idx in 0 until _size)
    val tmp = data[rootOffset] as? Cell<E> ?: return data[rootOffset] as E
    if (idx == 0) return tmp.value
    val subtree = tmp[subLevel]
    return _get(subtree.offset2nd + idx - 1)
  }
  
  override fun get(dim: Index): E =
      operation(dim.iterator()) { offset, _ -> _get(offset) }
  
  override fun set(dim: Index, s: E) =
      operation(dim.iterator()) { offset, _ -> _set(offset, s) }
  
  override fun set(element_maker: (Index, E) -> E) {
    dfs(rootOffset, subLevel) { slot, offset ->
      _set(offset, element_maker(slot, _get(offset)))
    }
  }
  
  private fun dfs(offset: Int, end: Int = 0, slot: MutableIntBuf = DefaultIntBuf.new(),
                  visit: (MutableIntBuf, Int) -> Unit) {
    val cell = data[offset] as? Cell<E> ?: return visit(slot, offset)
    val subtrees = cell.subtrees
    //prevent the size from changing. It should only increase.
    val subtrees_size = subtrees.size
    slot.append(subtrees_size, 0)
    visit(slot, offset)//access the leaf node
    for (level in subtrees_size - 1 downTo end) {
      slot.removeLast(1)
      val subtree = subtrees[level]
      if (subtree.size == 1) continue
      for (idx in 1 until subtree.size) {
        slot.append(idx)
        dfs(subtree.offset2nd + idx - 1, 0, slot, visit)
        slot.removeLast(1)
      }
    }
    slot.removeLast(end)
  }
  
  /**
   * expand the leaf at [offset] to branch node of [size]
   */
  internal fun expand(offset: Int, size: Int): SubTree {
    val tmp = data[offset] as? Cell<E>
              ?: Cell(DefaultBuf.new(), data[offset] as E)
    val subtree = SubTree(size = size,
                          offset2nd = data.writePtr)
    tmp.subtrees.append(subtree)
    data[offset] = tmp
    data.unfold(size - 1)
    return subtree
  }
  
  override fun indices() = Itr { slot, _ -> slot }
  
  override fun withIndices(): Iterator<tuple2<out IntBuf, E>> {
    var idxElement: tuple2<out IntBuf, E>? = null
    return Itr { slot, e ->
      idxElement?.apply { _2 = e }
      ?: tuple2(slot, e).apply { idxElement = this }
    }
  }
  
  inner class Itr<T>(
      private val visitor: (IntBuf, E) -> T
  ): Iterator<T> {
    private var offset = rootOffset
    private var visited = 0
    private val stack = LinkedList<tuple2<SubTree, Int>>()
    private val slot = DefaultIntBuf.new()
    
    override fun hasNext() = visited < _size
    
    override fun next(): T {
      //correct the slot
      if (stack.isEmpty())
        deepDown(subLevel)
      else while (true) {
        val toVisit = stack.peek()
        if (toVisit._2 < toVisit._1.lastIndex) {
          toVisit._2++
          slot[slot.lastIndex]++
          offset = toVisit._1.offset2nd + toVisit._2 - 1
          deepDown()
          break
        } else {//finish visited this SubTree
          stack.pop()
          slot.removeLast(1)
        }
      }
      visited++
      val e = _get(offset)
      return visitor(slot, e)
    }
    
    private fun deepDown(level: Int = 0) {
      val cell = data[offset] as? Cell<E>
      if (cell != null) {
        val subtrees = cell.subtrees
        subtrees.forEach(level) { _, subtree ->
          stack.push(tuple2(subtree, 0))
        }
        slot.append(subtrees.size - level, 0)
      }
    }
  }
  
  override val size: Int
    get():Int {
      if (_size < 0) {
        _size = if (data.isEmpty) 0
        else {
          val tmp = data[rootOffset] as? Cell<E>
          when {
            tmp == null -> 1
            subLevel > tmp.subtrees.lastIndex -> 1
            else -> {
              val tmpSubtree = tmp[subLevel]
              tmpSubtree.offsetEnd - tmpSubtree.offset2nd + 2
            }
          }
        }
      }
      return _size
    }
  
  override fun iterator() = object: Iterator<E> {
    val offset2nd: Int
    
    init {
      offset2nd =
          if (_size <= 1) 0
          else {
            val subtree = (data[rootOffset] as Cell<E>)[subLevel]
            subtree.offset2nd
          }
    }
    
    var a = 0
    override fun hasNext() = a < _size
    
    override fun next() = _get(if (a == 0) rootOffset else offset2nd + a - 1).apply { a++ }
  }
  
  override fun toString(): String {
    val sb = StringBuilder()
    for ((idx, value) in withIndices()) {
      sb.append("$idx=$value").append("\n")
    }
    return sb.toString()
  }
}