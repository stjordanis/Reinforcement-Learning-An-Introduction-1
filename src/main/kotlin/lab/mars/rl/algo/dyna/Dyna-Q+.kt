package lab.mars.rl.algo.dyna

import lab.mars.rl.algo.V_from_Q_ND
import lab.mars.rl.algo.`ε-greedy`
import lab.mars.rl.model.*
import lab.mars.rl.util.buf.DefaultBuf
import lab.mars.rl.util.debug
import lab.mars.rl.util.max
import lab.mars.rl.util.tuples.tuple2
import lab.mars.rl.util.tuples.tuple3
import org.apache.commons.math3.util.FastMath.sqrt
import org.slf4j.LoggerFactory

@Suppress("NAME_SHADOWING")
class `Dyna-Q+`(val mdp: MDP) {
    companion object {
        val log = LoggerFactory.getLogger(this::class.java)!!
    }

    val γ = mdp.γ
    val started = mdp.started
    val states = mdp.states
    var stepListener: (ActionValueFunction, State) -> Unit = { _, _ -> }
    var episodeListener: (StateValueFunction) -> Unit = {}

    var episodes = 10000
    var α = 0.1
    var ε = 0.1
    var κ = 1e-4
    var n = 10
    val null_tuple3 = tuple3(null_state, Double.NaN, 0)
    fun optimal(_alpha: (State, Action) -> Double = { _, _ -> α }): OptimalSolution {
        val π = mdp.QFunc { 0.0 }
        val Q = mdp.QFunc { 0.0 }
        val cachedSA = DefaultBuf.new<tuple2<State, Action>>(Q.size)
        val Model = mdp.QFunc { null_tuple3 }
        val V = mdp.VFunc { 0.0 }
        val result = tuple3(π, V, Q)
        var time = 0
        for (episode in 1..episodes) {
            log.debug { "$episode/$episodes" }
            var s = started.rand()
            while (s.isNotTerminal()) {
                V_from_Q_ND(states, result)
                stepListener(V, s)
                time++
                `ε-greedy`(s, Q, π, ε)
                val a = s.actions.rand(π(s))
                val (s_next, reward, _) = a.sample()
                Q[s, a] += _alpha(s, a) * (reward + γ * max(s_next.actions, 0.0) { Q[s_next, it] } - Q[s, a])
                for (_a in s.actions) {
                    if (_a !== a && Model[s, _a] === null_tuple3) {
                        cachedSA.append(tuple2(s, _a))
                        Model[s, _a] = tuple3(s, 0.0, 1)
                    }
                }
                if (Model[s, a] === null_tuple3)
                    cachedSA.append(tuple2(s, a))
                Model[s, a] = tuple3(s_next, reward, time)
                repeat(n) {
                    val (s, a) = cachedSA.rand()
                    var (s_next, reward, t) = Model[s, a]
                    reward += κ * sqrt((time - t).toDouble())
                    Q[s, a] += _alpha(s, a) * (reward + γ * max(s_next.actions, 0.0) { Q[s_next, it] } - Q[s, a])
                }
                s = s_next
            }
            episodeListener(V)
            log.debug { "steps=$time" }
        }
        return result
    }
}