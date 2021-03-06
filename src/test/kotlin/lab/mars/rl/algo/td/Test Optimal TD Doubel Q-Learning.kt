package lab.mars.rl.algo.td

import lab.mars.rl.algo.average_α
import lab.mars.rl.model.isNotTerminal
import lab.mars.rl.problem.Blackjack
import lab.mars.rl.problem.CliffWalking
import lab.mars.rl.problem.MaximizationBias
import lab.mars.rl.problem.WindyGridworld
import lab.mars.rl.util.math.argmax
import lab.mars.rl.util.printBlackjack
import org.junit.Test

class `Test Optimal TD Doubel Q-Learning` {
  @Test
  fun `Blackjack constant alpha`() {
    val (prob) = Blackjack.make()
    val (π, V) = prob.DoubleQLearning(ε = 0.1, α = { _, _ -> 0.1 }, episodes = 1000000)
    printBlackjack(prob, π, V)
  }
  
  @Test
  fun `Blackjack average alpha`() {
    val (prob) = Blackjack.make()
    val (π, V) = prob.DoubleQLearning(ε = 0.1, α = average_α(prob), episodes = 1000000)
    printBlackjack(prob, π, V)
  }
  
  @Test
  fun `Cliff Walking`() {
    val prob = CliffWalking.make()
    val (π) = prob.DoubleQLearning(ε = 0.1, α = { _, _ -> 0.5 }, episodes = 10000)
    var s = prob.started()
    var sum = 0.0
    print(s)
    while (s.isNotTerminal) {
      val a = argmax(s.actions) { π[s, it] }
      val possible = a.sample()
      s = possible.next
      sum += possible.reward
      print("${WindyGridworld.desc_move[a[0]]}$s")
    }
    println("\nreturn=$sum")//optimal=-12
  }
  
  @Test
  fun `Maximization Bias Double Q-Learning`() {
    val prob = MaximizationBias.make()
    val (π) = prob.QLearning(ε = 0.1, α = { _, _ -> 0.1 }, episodes = 10)
    val A = prob.started()
    println(π(A))
    
    val (π2) = prob.DoubleQLearning(ε = 0.1, α = { _, _ -> 0.1 }, episodes = 10)
    println(π2(A))
    
  }
}