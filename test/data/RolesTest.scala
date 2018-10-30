package data

import models.Dependency
import models._
import org.joda.time.DateTime
import org.scalatest.{ FlatSpec, Matchers }

class RolesTest extends FlatSpec with Matchers {

  it should "find all transitive dependencies" in {
    val r0 = RoleSummary(RoleId("id0"), Set(RoleId("id1"), RoleId("id2")), null, null)
    val r1 = RoleSummary(RoleId("id1"), Set(RoleId("id2")), null, null)
    val r2 = RoleSummary(RoleId("id2"), Set(RoleId("id3")), null, null)
    val r3 = RoleSummary(RoleId("id3"), Set.empty, null, null)

    val roles = List(r0, r1, r2, r3)
    Roles.transitiveDependencies(roles, r0.roleId).dependencies should contain only (Dependency(RoleId("id1"), Set(Dependency(RoleId("id2"), Set(Dependency(RoleId("id3"), Set()))))), Dependency(RoleId("id2"), Set(Dependency(RoleId("id3"), Set()))))
  }

  it should "return direct dependencies if there are no transitive" in {
    val r0 = RoleSummary(RoleId("id0"), Set(RoleId("id1"), RoleId("id2")), null, null)
    val r1 = RoleSummary(RoleId("id1"), Set(RoleId("id2")), null, null)
    val r2 = RoleSummary(RoleId("id2"), Set.empty, null, null)
    val r3 = RoleSummary(RoleId("id3"), Set(RoleId("id2")), null, null)

    val roles = List(r0, r1, r2, r3)
    Roles.transitiveDependencies(roles, r0.roleId).dependencies should contain only (Dependency(RoleId("id1"), Set(Dependency(RoleId("id2"), Set()))), Dependency(RoleId("id2"), Set()))
  }

  it should "return empty if there are no dependencies" in {
    val r0 = RoleSummary(RoleId("id0"), Set(RoleId("id1"), RoleId("id2")), null, null)
    val r1 = RoleSummary(RoleId("id1"), Set(RoleId("id2")), null, null)
    val r2 = RoleSummary(RoleId("id2"), Set.empty, null, null)
    val r3 = RoleSummary(RoleId("id3"), Set(RoleId("id2")), null, null)

    val roles = List(r0, r1, r2, r3)
    Roles.transitiveDependencies(roles, r2.roleId).dependencies.isEmpty should be(true)
  }

  it should "find recipes that use this role" in {
    val rec1 = Recipe(RecipeId("r1"), None, null, None, List(CustomisedRole(RoleId("apt"), Map.empty), CustomisedRole(RoleId("java8"), Map.empty)), "creator", DateTime.now(), "modifiedBy", DateTime.now(), None, Nil)
    val rec2 = Recipe(RecipeId("r2"), None, null, None, List(CustomisedRole(RoleId("apt"), Map.empty)), "creator", DateTime.now(), "modifiedBy", DateTime.now(), None, Nil)
    val rec3 = Recipe(RecipeId("r3"), None, null, None, List(CustomisedRole(RoleId("java8"), Map.empty)), "creator", DateTime.now(), "modifiedBy", DateTime.now(), None, Nil)
    val allRecipes = List(rec1, rec2, rec3)
    val usedByRecipes = Roles.usedByRecipes(allRecipes, RoleSummary(RoleId("java8"), Set.empty, null, null))
    usedByRecipes should contain only (rec1.id, rec3.id)
  }
}
