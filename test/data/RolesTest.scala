package data

import data.Roles.Dependency
import models._
import org.scalatest.{ Matchers, FlatSpec }

class RolesTest extends FlatSpec with Matchers {

  it should "find all transitive dependencies" in {
    val r0 = RoleSummary(RoleId("id0"), Set(RoleId("id1"), RoleId("id2")), null, null)
    val r1 = RoleSummary(RoleId("id1"), Set(RoleId("id2")), null, null)
    val r2 = RoleSummary(RoleId("id2"), Set(RoleId("id3")), null, null)
    val r3 = RoleSummary(RoleId("id3"), Set.empty, null, null)

    val roles = List(r0, r1, r2, r3)
    Roles.transitiveDependencies(roles, r0).dependencies should contain only (Dependency(RoleId("id1"), Set(Dependency(RoleId("id2"), Set(Dependency(RoleId("id3"), Set()))))), Dependency(RoleId("id2"), Set(Dependency(RoleId("id3"), Set()))))
  }

  it should "return direct dependencies if there are no transitive" in {
    val r0 = RoleSummary(RoleId("id0"), Set(RoleId("id1"), RoleId("id2")), null, null)
    val r1 = RoleSummary(RoleId("id1"), Set(RoleId("id2")), null, null)
    val r2 = RoleSummary(RoleId("id2"), Set.empty, null, null)
    val r3 = RoleSummary(RoleId("id3"), Set(RoleId("id2")), null, null)

    val roles = List(r0, r1, r2, r3)
    Roles.transitiveDependencies(roles, r0).dependencies should contain only (Dependency(RoleId("id1"), Set(Dependency(RoleId("id2"), Set()))), Dependency(RoleId("id2"), Set()))
  }

  it should "return empty if there are no dependencies" in {
    val r0 = RoleSummary(RoleId("id0"), Set(RoleId("id1"), RoleId("id2")), null, null)
    val r1 = RoleSummary(RoleId("id1"), Set(RoleId("id2")), null, null)
    val r2 = RoleSummary(RoleId("id2"), Set.empty, null, null)
    val r3 = RoleSummary(RoleId("id3"), Set(RoleId("id2")), null, null)

    val roles = List(r0, r1, r2, r3)
    Roles.transitiveDependencies(roles, r2).dependencies.isEmpty should be(true)
  }
}
