package enju.ccg.parser

import enju.ccg.lexicon.{PoS, Word, Category, TrainSentence, Derivation, BinaryChildrenPoints, UnaryChildPoint}
import enju.ccg.lexicon.Direction._

/**
 * Define the oracle for transition-based systems.
 * Terms in this file are borrowed from a paper,
 *   Training Deterministic Parsers with Non-Deterministic Oracles (Goldberg and Nivre, 2013),
 * which discuss the design of oracles, especially dynamic oracle to alleviate error propagations of the deterministic (non-beam) transition-systems.
 * Dynamic oracle is originally introduced in the arc-eager system of dependency parsing.
 *
 * The trait Oracle defined below abstract away the mechanism of concrete oracle. However, crrently it is unknown whether dynamic oracle can be applied to our consituency parsing, so StaticArcStandardOracle, which returns only one action that leads to the gold parse, is the valid one.
 * For future, we may construct an oracle which deals with spurious ambiguity of CCG, so the method goldActions returns the set of valid actions, but current version (Static...) alwasy returns the set of only one element.
 */
trait Oracle {
  def goldActions(state:State):Seq[Action]
}
class StaticArcStandardOracle(val sentence:TrainSentence, val gold:Derivation, val rule:Rule) extends Oracle {
  override def goldActions(state:State) = {
    def isFinished = state.j == sentence.size && onlyContainRootNode
    def onlyContainRootNode = (state.s1, state.s0) match {
      case (None, Some(s0)) => s0.toDerivationPoint == gold.root
      case _ => false
    }
    def combineAction: Option[Action] = for {
      s1 <- state.s1; s0 <- state.s0
      goldParent <- gold.parentCategory(BinaryChildrenPoints(s1.toDerivationPoint, s0.toDerivationPoint))
    } yield { Combine(goldParent, directionAfterCombine(s1.head, s0.head)) }
    
    def directionAfterCombine(l:Int, r:Int) = rule.headFinder.get(sentence.pos(l), sentence.pos(r))
    def unaryAction: Option[Action] = for {
      s0 <- state.s0
      goldParent <- gold.parentCategory(UnaryChildPoint(s0.toDerivationPoint))
    } yield { Unary(goldParent) }
    
    require (state.isGold)
    val retAction = if (isFinished) Finish() else combineAction match {
      case Some(action) => action
      case None => unaryAction match {
        case Some(action) => action
        case None if state.j < sentence.size => Shift(sentence.cat(state.j))
        case _ => sys.error("ERROR: could not find any gold actions")
      }
    }
    List(retAction) // only one element is valid
  }
}

// class NonDeterministicArcStandardOracle(val sentence:TrainSentence, val gold:Derivation) extends Oracle {
    
// }

trait OracleGenerator {
  type O <: Oracle
  def gen(sentence:TrainSentence, gold:Derivation, rule:Rule):O
}
object StaticOracleGenerator extends OracleGenerator {
  type O = StaticArcStandardOracle
  override def gen(sentence:TrainSentence, gold:Derivation, rule:Rule) = new StaticArcStandardOracle(sentence, gold, rule)
}
// object NonDeterministicOracleGenerator extends OracleGenerator {
//   override def gen(sentence:TrainSentence, gold:Derivation, rule:Rule) = 
//     new NonDeterministicArcStandardOracle(sentence, gold, rule)
// }
