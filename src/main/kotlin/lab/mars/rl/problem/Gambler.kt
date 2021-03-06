package lab.mars.rl.problem

import lab.mars.rl.model.impl.mdp.CNSetMDP
import lab.mars.rl.model.impl.mdp.IndexedMDP
import lab.mars.rl.model.impl.mdp.IndexedPossible
import lab.mars.rl.util.collection.cnsetOf
import org.apache.commons.math3.util.FastMath.min

/**
 * <p>
 * Created on 2017-09-13.
 * </p>
 *
 * @author wumo
 */
object Gambler {
  val goal_coin = 100
  
  fun make(p_head: Double): IndexedMDP {
    val mdp = CNSetMDP(gamma = 1.0,
                       state_dim = goal_coin + 1,
                       action_dim = { min(it[0], goal_coin - it[0]) + 1 })
    mdp.apply {
      for (s in states) {
        val capital = s[0]
        val max_stake = min(capital, goal_coin - capital)
        for (action in s.actions) {
          val stake = action[0]
          action.possibles = if (max_stake == 0)
            cnsetOf(IndexedPossible(states[capital], 0.0, 1.0))
          else
            cnsetOf(IndexedPossible(states[capital - stake], 0.0, 1 - p_head), //lose
                    IndexedPossible(states[capital + stake], if (capital + stake == goal_coin) 1.0 else 0.0, p_head))//win
        }
      }
    }
    return mdp
  }
}