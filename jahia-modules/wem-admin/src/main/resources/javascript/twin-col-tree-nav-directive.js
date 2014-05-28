'use strict';

angular.module('twinColTreeNav', [])
  .directive('twinColTreeNav', function () {
    var template =
      '<div class="twin-col-tree-nav">' +
      '  <!-- Bread crumb -->' +
      '  <div class="bread-crumb">' +
      '    <span ng-repeat="elem in breadCrumb" class="bread-crumb-elem">' +
      '      <a href="" ng-click="selectBreadCrumbElement(elem)">' +
      '        {{elem.name}}<span ng-if="hasChildren(elem)"> &#9658;</span>' +
      '      </a>' +
      '    </span>' +
      '  </div>' +
      '  <hr>' +
      '  <!-- twin col -->' +
      '  <div class="twin-cols row">' +
      '    <!-- Left -->' +
      '    <div class="col-md-4"><div class="left-column list-group">' +
      '        <a class="list-group-item" ng-repeat="root in currentRoots" ng-class="{active: isSelected(root)}" href="" ng-click="selectRoot(root)">' +
      '          {{root.name}}' +
      '          <span class="child-pointer" ng-if="hasChildren(root)"> &#9658;</span>' +
      '        </a>' +
      '    </div></div>' +
      '    <!-- Right -->' +
      '    <div class="col-md-4"><div class="right-column list-group">' +
      '        <a ng-repeat="child in childrenOfSelectedRoot" class="list-group-item" ui-draggable="true" drag="child" href="" ng-click="selectChild(child, childrenOfSelectedRoot)" ng-class="{active: isSelected(child)}">' +
      '          {{child.name}}' +
      '          <span class="child-pointer" ng-if="hasChildren(child)"> &#9658;</span>' +
      '        </a>' +
      '    </div></div>' +
      '    <div class="col-md-4" ui-draggable="true" drag="selectedTreeRoot">' +
      '      {{selectedTreeRoot}}' +
      '    </div>' +
      '  </div>' +
      '</div>';
    return {
      template: template,
      restrict: 'EA',
      scope: {
        treeHandle: '='
      },
      controller: function ($scope) {
        function init() {
          var initialChildren = $scope.treeHandle.getChildren();
          var firstChild = initialChildren && initialChildren.length > 0 && initialChildren[0];
          $scope.selectChild(firstChild, initialChildren);
        }

        $scope.treeHandle.atSelectTreeNode = $scope.treeHandle.atSelectTreeNode || function () {
          // Making sure there is a default implementation
        };
        $scope.treeHandle.getChildren = $scope.treeHandle.getChildren || function () {
          // Making sure there is a default implementation
          return [];
        };
        $scope.treeHandle.hasChildren = $scope.treeHandle.hasChildren || function () {
          // Making sure there is a default implementation
          return false;
        };
        // The atTreeUpdate() function is intended for facilitating callback from
        // tree provider when the tree data has been updated
        $scope.treeHandle.atTreeUpdate = function (parentTreeNode) {
          // TODO handle partial update of the specified parentTreeNode (i.e. a child has been added or removed)
          // For now reset view
          init();
        };

        function getParent(curBreadCrumb, breadCrumbElem) {
          if (curBreadCrumb) {
            for (var i = 0; i < curBreadCrumb.length; i++) {
              if (curBreadCrumb[i] === breadCrumbElem) {
                return i > 0 && curBreadCrumb[i - 1] || undefined;
              }
            }
          } else {
            return undefined;
          }
        }

        function updatedBreadCrumb(curBreadCrumb, treeNode, appendChild, rebuildBreadCrumb) {
          var newBreadCrumb = [];
          if (rebuildBreadCrumb) {
            // Reset bread crumb, i.e. find the point where the tree node is in the bread crumb
            for (var i = 0; i < curBreadCrumb.length; i++) {
              newBreadCrumb.push(curBreadCrumb[i]);
              if (curBreadCrumb[i] === treeNode) {
                return newBreadCrumb;
              }
            }
          } else if (appendChild) {
            // Just append the new tree node
            for (var i = 0; i < curBreadCrumb.length; i++) {
              newBreadCrumb.push(curBreadCrumb[i]);
            }
            newBreadCrumb.push(treeNode);
            return newBreadCrumb;
          } else {
            // the new root is a sibling to the previous root replace last element
            for (var i = 0; i < curBreadCrumb.length; i++) {
              newBreadCrumb.push(curBreadCrumb[i]);
            }
            newBreadCrumb.pop();
            newBreadCrumb.push(treeNode);
            return newBreadCrumb;
          }
        }

        $scope.currentRoots = $scope.treeHandle.getChildren();

        $scope.childrenOfSelectedRoot = [];

        $scope.breadCrumb = [];

        $scope.isSelected = function(treeNode) {
          return $scope.selectedTreeRoot.name === treeNode.name;
        };

        $scope.hasChildren = function (treeNode) {
          return $scope.treeHandle.hasChildren(treeNode);
        };

        $scope.selectBreadCrumbElement = function (breadCrumbElem) {
          var siblings = $scope.treeHandle.getChildren(getParent($scope.breadCrumb, breadCrumbElem));
          $scope.selectChild(breadCrumbElem, siblings, true);
        };

        $scope.selectRoot = function selectRoot(root, appendChild, rebuildBreadCrumb) {
          $scope.selectedTreeRoot = root;
          $scope.breadCrumb = updatedBreadCrumb($scope.breadCrumb, root, appendChild, rebuildBreadCrumb);
          $scope.childrenOfSelectedRoot = $scope.treeHandle.getChildren(root);
          $scope.treeHandle.atSelectTreeNode(root);
        };

        $scope.selectChild = function selectChild(child, siblings, rebuildBreadCrumb) {
          if ($scope.hasChildren(child)) {
            $scope.currentRoots = siblings;
            $scope.selectRoot(child, true, rebuildBreadCrumb);
          }
          $scope.treeHandle.atSelectTreeNode(child);
        };

        init();
      },
      link: function postLink(scope, element, attrs) {
      }
    };

  });